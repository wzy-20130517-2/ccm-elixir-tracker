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
        val elixirReal: Boolean,
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
    private var lastLocate = 0L
    private var locateAttempts = 0

    fun start(scope: CoroutineScope) {
        if (!running.compareAndSet(false, true)) return
        elixir.primeGuess(5)
        job = scope.launch(Dispatchers.IO) {
            while (running.get()) {
                val t0 = System.currentTimeMillis()
                runCatching { step() }.onFailure { err ->
                    onFrame(State(false, pid, elixir.update(null), false, emptyList(), emptyList(), emptyList(), false))
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
        elixir.reset()
        elixir.primeGuess(5)
        locateAttempts = 0
        lastLocate = 0
    }

    private fun step() {
        val now = System.currentTimeMillis()

        if (pid <= 0 || now - lastPidCheck > 5000) {
            val newPid = runner.gamePid()
            if (newPid != pid) {
                // 游戏重启 → 圣水地址作废
                elixir.setAddress(0)
                tracker.reset()
                locateAttempts = 0
                lastLocate = 0
            }
            pid = newPid
            lastPidCheck = now
        }

        if (pid <= 0) {
            onFrame(State(false, -1, elixir.update(null), false, emptyList(), emptyList(), emptyList(), false))
            return
        }

        // 圣水地址：尝试 3 次后就放弃，只靠外推
        if (!elixir.hasAddress() && locateAttempts < 3 && now - lastLocate > 20_000) {
            lastLocate = now
            locateAttempts++
            locateElixir()
        }

        val raw = readElixirRaw()
        val elxVal = elixir.update(raw)

        val entities = parse(runner.execRoot("crexec", pid.toString(), timeoutSec = 20))
        val towers = entities.filter { it.isTower }
        val inBattle = towers.size >= 4

        val events = tracker.onFrame(entities, now)

        onFrame(
            State(
                ok = true, pid = pid, elixir = elxVal, elixirReal = raw != null,
                entities = entities, newDeploys = events,
                enemyKnown = tracker.knownCards(), inBattle = inBattle,
            )
        )
    }

    private fun locateElixir() {
        val out = runner.execRoot("elixir_find", pid.toString(), "3", timeoutSec = 40)
        out.lineSequence().firstOrNull { it.isNotBlank() }?.let { line ->
            line.trim().split(Regex("\\s+")).getOrNull(0)?.toLongOrNull()?.let { elixir.setAddress(it) }
        }
    }

    private fun readElixirRaw(): Int? {
        if (!elixir.hasAddress()) return null
        val out = runner.execRoot("rd4", pid.toString(), elixir.address().toString(), timeoutSec = 8)
        return out.lineSequence().firstOrNull()?.trim()?.toIntOrNull()
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