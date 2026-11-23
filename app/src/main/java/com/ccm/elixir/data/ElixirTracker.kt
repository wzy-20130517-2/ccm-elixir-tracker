package com.ccm.elixir.data

import kotlin.math.min

/**
 * 我方圣水值。
 *
 * 地址只在开局几秒内有效（出牌后对象重建），所以有两套来源：
 *  1. 真实地址读到 [0,10] → 直接用
 *  2. 读不到 → 沿用上一次真值，按 2.8s/+1 外推（封顶 10）
 */
class ElixirTracker {

    private var addr: Long = 0
    private var lastGood: Int = 5
    private var anchorMs: Long = 0

    /** 双倍圣水期（最后 60 秒） */
    var multiplier: Double = 1.0
        set(value) { field = value; anchorMs = System.currentTimeMillis() }

    fun setAddress(a: Long) { addr = a }

    fun hasAddress(): Boolean = addr != 0L

    fun address(): Long = addr

    /** 开局先给个估算值，让 UI 有东西显示 */
    fun primeGuess(v: Int) {
        lastGood = v.coerceIn(0, 10)
        anchorMs = System.currentTimeMillis()
    }

    /**
     * 根据当前读到的原始值更新，返回显示用的值。
     * @param raw 从 addr 读到的值，读失败传 null
     */
    fun update(raw: Int?): Int {
        val now = System.currentTimeMillis()
        if (raw != null && raw in 0..10) {
            lastGood = raw
            anchorMs = now
            return raw
        }
        // 外推
        val periodMs = (2800 / multiplier).toLong().coerceAtLeast(1000)
        val elapsed = now - anchorMs
        val gained = (elapsed / periodMs).toInt()
        return min(10, lastGood + gained)
    }

    fun reset() {
        addr = 0
        lastGood = 5
        anchorMs = System.currentTimeMillis()
        multiplier = 1.0
    }
}