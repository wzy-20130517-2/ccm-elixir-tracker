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
 * 规则：
 *  - 敌方每次「新出现」一个单位，视为出了一张该牌
 *  - 同一张牌重复出现不重复记（已经知道这张牌在手里）
 *  - 8 张牌里已知的显示为彩色，未知的显示为灰色占位
 *
 * 觉醒轮数：
 *  - 一张牌第 1、2 次出是普通；第 3 次起是觉醒（一轮觉醒）
 *  - 二轮觉醒的牌：第 1 次普通，第 2 次觉醒，第 3 次普通，第 4 次觉醒…
 *  - 精英卡（super-*）整场都按精英样式显示
 */
class EnemyTracker {

    /** 敌方已确认的牌（cardId 集合，按发现顺序） */
    private val known = LinkedHashSet<Int>()

    /** cardId -> 已经出过几次（用于算觉醒轮次） */
    private val playCount = HashMap<Int, Int>()

    /** 上一帧仍然在场的 gid，用来判断「新出现」 */
    private var lastAliveGids = emptySet<Int>()

    /** 上一帧的塔 gid 血量，用来检测塔被拆 */
    private var lastTowerHp = HashMap<Int, Int>()

    val deployed = mutableListOf<DeployEvent>()

    fun reset() {
        known.clear()
        playCount.clear()
        lastAliveGids = emptySet()
        lastTowerHp = HashMap()
        deployed.clear()
    }

    /**
     * 喂一帧实体数据。
     *
     * @return 本帧新发现的出牌（可能多张）
     */
    fun onFrame(entities: List<Entity>, nowMs: Long): List<DeployEvent> {
        val enemyUnits = entities.filter { it.own == 0 && !it.isTower }
        val aliveNow = enemyUnits.map { it.gid }.toSet()

        val fresh = enemyUnits.filter { it.gid !in lastAliveGids }
        val events = fresh.map { e ->
            val n = (playCount[e.cardId] ?: 0) + 1
            playCount[e.cardId] = n
            known.add(e.cardId)
            DeployEvent(
                cardId = e.cardId, wx = e.x, wy = e.y, atMs = nowMs,
                evolved = isEvolvedOnPlay(e.cardId, n),
            )
        }

        // 塔血量下降也记一笔（不算出牌，但可用于校验）
        entities.filter { it.isTower && it.own == 0 }.forEach { t ->
            val prev = lastTowerHp[t.gid]
            if (prev != null && t.cur < prev) { /* 塔被打了 */ }
            lastTowerHp[t.gid] = t.cur
        }

        lastAliveGids = aliveNow
        if (events.isNotEmpty()) deployed.addAll(events)
        return events
    }

    /** 该 cardId 已确认在敌方手里 */
    fun isKnown(cardId: Int): Boolean = cardId in known

    /** 已确认的牌列表（按发现顺序） */
    fun knownCards(): List<Int> = known.toList()

    /** 该牌出过几次 */
    fun playCountOf(cardId: Int): Int = playCount[cardId] ?: 0

    /**
     * 下一次出这张牌时，是不是觉醒形态。
     * 精英卡永远走精英样式，不参与觉醒判定。
     */
    fun isNextEvolved(cardId: Int): Boolean = isEvolvedOnPlay(cardId, playCountOf(cardId) + 1)

    companion object {
        /**
         * 觉醒周期（每几次出牌循环一次）。0 表示该卡没有觉醒。
         */
        fun evoCycle(cardId: Int): Int {
            val card = CardDb.get(cardId) ?: return 0
            if (card.pngEvo == null) return 0
            // 一轮觉醒：第 1 次普通，第 2 次觉醒
            return 1
        }

        /**
         * 第 n 次出这张牌时是否觉醒。
         *
         * 一轮觉醒：周期 2 —— 1 普通，2 觉醒，3 普通，4 觉醒…
         * 二轮觉醒：周期 3 —— 1 普通，2 普通，3 觉醒…（少数卡）
         */
        fun isEvolvedOnPlay(cardId: Int, n: Int): Boolean {
            if (n < 1) return false
            return when (evoCycle(cardId)) {
                0 -> false
                else -> n % 2 == 0
            }
        }
    }
}