package com.ccm.elixir.data

import com.ccm.elixir.native.NativeRunner
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 战斗状态轮询器：每 ~500ms 读一次实体，驱动圣水和记牌。
 *
 * 节奏：
 *  - 实体扫描 500ms 一次（crexec 约 300-500ms）
 *  - 圣水 500ms 一次（rd4，约 30ms）
 *  - pid / 圣水地址定位按需触发
 */
class BattlePoller(
    private val runner: NativeRunner,
    private val onFrame: (State) -> Unit,
) {
    data class State(
        val ok: Boolean,
        val pid: Int,
        val elixir: Int,
        val elixirRunning: Boolean,
        val entities: List<Entity>,
        val newDeploys: List<DeployEvent>,
        val enemyKnown: List<Int>,
        val inBattle: Boolean,
    )

    val elixir = ElixirTracker()
    val tracker = EnemyTracker()

    private var job: Job? = null
    private val running = AtomicBoolean(false)
    private var pid = -1
    private var lastPidCheck = 0L

    fun start(scope: CoroutineScope) {
        if (!running.compareAndSet(false, true)) return
        job = scope.launch(Dispatchers.IO) {
            while (running.get()) {
                val t0 = System.currentTimeMillis()
                runCatching { step() }.onFailure { err ->
                    onFrame(State(false, pid, elixir.poll(), elixir.running, emptyList(), emptyList(), emptyList(), false))
                }
                val spent = System.currentTimeMillis() - t0
                delay((500 - spent).coerceAtLeast(50))
            }
        }
    }

    fun stop() {
        running.set(false)
        job?.cancel()
        job = null
    }

    fun resetBattle() {
        tracker.reset()
        elixir.stop()
    }

    private fun step() {
        val now = System.currentTimeMillis()

        if (pid <= 0 || now - lastPidCheck > 5000) {
            val newPid = runner.gamePid()
            if (newPid != pid) {
                // 游戏重启 → 重置记牌和计时
                tracker.reset()
                elixir.stop()
            }
            pid = newPid
            lastPidCheck = now
        }

        if (pid <= 0) {
            onFrame(State(false, -1, elixir.poll(), elixir.running, emptyList(), emptyList(), emptyList(), false))
            return
        }

        val entities = parse(runner.execRoot("crexec", pid.toString(), timeoutSec = 20))
        val towers = entities.filter { it.isTower }
        val inBattle = towers.size >= 4

        val events = tracker.onFrame(entities, now)

        // 敌方出牌 -> 扣费（重牌长静默后出现则重挂为 10）
        events.forEach { ev ->
            val cost = CardDb.get(ev.cardId)?.elixir ?: 0
            elixir.onEnemyPlay(cost, now)
        }

        onFrame(
            State(
                ok = true, pid = pid, elixir = elixir.poll(), elixirRunning = elixir.running,
                entities = entities, newDeploys = events,
                enemyKnown = tracker.deck(), inBattle = inBattle,
            )
        )
    }

    private fun parse(text: String): List<Entity> {
        val list = ArrayList<Entity>(64)
        text.lineSequence().forEach { line ->
            val t = line.trim().split(Regex("\\s+"))
            if (t.size < 7) return@forEach
            val v = t.mapNotNull { it.toIntOrNull() }
            if (v.size < 7) return@forEach
            list.add(Entity(v[0], v[1], v[2], v[3], v[4], v[5], v[6]))
        }
        return list
    }
}