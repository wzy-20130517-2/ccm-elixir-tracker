package com.ccm.elixir.data

/**
 * 一个战斗实体（从 native 读到的快照）。
 */
data class Entity(
    val gid: Int,
    val own: Int,        // 1=我方 0=敌方
    val x: Int,
    val y: Int,
    val cur: Int,
    val max: Int,
    val cardId: Int,     // -1 = 塔
) {
    val isTower: Boolean get() = cardId == -1
}

/**
 * 敌方出牌的一次观测（用于落点预判浮层）。
 */
data class DeployEvent(
    val cardId: Int,
    val wx: Int,
    val wy: Int,
    val atMs: Long,
    val evolved: Boolean,
)

/**
 * 敌方牌面追踪。
 *
 * 核心模型（8 张牌循环）：
 *   敌方卡组固定 8 张，手牌 4 张，出 1 张补 1 张。
 *   所以：**出过的牌按顺序排，最近 4 张不在手里，第 5 张（倒数第 4 张）= 下一张。**
 *
 *   hand = deck 里不在 played[-4:] 的那些
 *   next = played[-4]（出满 4 张后才有）
 *
 * 觉醒轮数：
 *   一轮觉醒的牌 —— 第 1 次普通，第 2 次觉醒，第 3 次普通…
 * 精英卡：
 *   整场保持精英外观
 */
class EnemyTracker {

    /** 敌方出过的牌，按时间顺序（可以有重复） */
    private val played = mutableListOf<Int>()

    /** 有足够证据确认属于敌方卡组的牌 */
    private val confirmed = LinkedHashSet<Int>()

    /** cardId -> 出过几次（算觉醒轮次） */
    private val playCount = HashMap<Int, Int>()

    private var lastAliveGids = emptySet<Int>()

    /** 本局全部出牌记录（给浮层用） */
    val deployLog = mutableListOf<DeployEvent>()

    fun reset() {
        played.clear()
        confirmed.clear()
        playCount.clear()
        lastAliveGids = emptySet()
        deployLog.clear()
    }

    /**
     * 喂一帧实体数据，返回本帧新发现的出牌。
     */
    fun onFrame(entities: List<Entity>, nowMs: Long): List<DeployEvent> {
        val enemyUnits = entities.filter { it.own == 0 && !it.isTower }
        val aliveNow = enemyUnits.map { it.gid }.toSet()

        val fresh = enemyUnits.filter { it.gid !in lastAliveGids }
        val events = fresh.map { e ->
            val n = (playCount[e.cardId] ?: 0) + 1
            playCount[e.cardId] = n
            confirmed.add(e.cardId)
            played.add(e.cardId)
            DeployEvent(
                cardId = e.cardId, wx = e.x, wy = e.y, atMs = nowMs,
                evolved = isEvolvedOnPlay(e.cardId, n),
            )
        }

        lastAliveGids = aliveNow
        if (events.isNotEmpty()) {
            deployLog.addAll(events)
            if (deployLog.size > 200) deployLog.subList(0, deployLog.size - 200).clear()
        }
        return events
    }

    /** 敌方已知的卡组（按出现次数降序，最多 8 张） */
    fun deck(): List<Int> {
        val order = played.distinct()
        return order.sortedByDescending { c -> played.count { it == c } }.take(8)
    }

    /** 敌方手牌 = 卡组里不在最近 4 张出牌中的 */
    fun hand(): List<Int> {
        val recent = played.takeLast(4).toSet()
        return deck().filter { it !in recent }
    }

    /** 下一张要回到手里的牌 = 倒数第 4 张出的 */
    fun nextCard(): Int? = if (played.size >= 4) played[played.size - 4] else null

    fun playCountOf(cardId: Int): Int = playCount[cardId] ?: 0

    fun isKnown(cardId: Int): Boolean = cardId in confirmed

    /** 给 Compose 做 remember 的 key：出牌数变化就重算 */
    fun playCountSnapshot(): Int = played.size

    /** 某张牌当前该用觉醒图吗（按已出次数推下一次） */
    fun showEvolved(cardId: Int): Boolean {
        val n = playCountOf(cardId)
        return if (n == 0) false else isEvolvedOnPlay(cardId, n)
    }

    /** 下一次出这张牌是不是觉醒形态 */
    fun isNextEvolved(cardId: Int): Boolean = isEvolvedOnPlay(cardId, playCountOf(cardId) + 1)

    /** 第 n 次出这张牌是否为觉醒 */
    fun isEvolvedOnPlay(cardId: Int, n: Int): Boolean {
        if (n < 1) return false
        val card = CardDb.get(cardId) ?: return false
        if (card.pngEvo == null) return false
        // 一轮觉醒：第 1 次普通，第 2 次觉醒
        return n % 2 == 0
    }
}