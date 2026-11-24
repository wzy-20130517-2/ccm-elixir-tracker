package com.ccm.elixir.data

import kotlin.math.min

/**
 * 敌方圣水估算。
 *
 * 敌方圣水不在内存里（服务端不下发对手圣水），只能推。
 *
 * 倍率时间轴（Supercell 官方规则）：
 *   第 1 分钟   1×
 *   第 2 分钟   1×
 *   第 3 分钟   2×
 *   加时第 4 分钟 2×
 *   加时第 5 分钟 3×
 * 常规 3 分钟分出胜负就直接结束，加时未必发生 —— 所以自动推演之外
 * 保留手动切倍率兜底。
 */
class ElixirTracker {

    private var value: Double = 5.0
    private var lastTickMs: Long = 0
    private var startedAtMs: Long = 0

    /** 是否在计时（暂停则冻结） */
    var running: Boolean = false
        private set

    /** 这一局是否已经点过开始 */
    var hasStarted: Boolean = false
        private set

    /** 当前倍率 */
    var phase: Phase = Phase.NORMAL
        private set

    enum class Phase(val multiplier: Double) {
        NORMAL(1.0),
        DOUBLE(2.0),
        TRIPLE(3.0),
    }

    val display: Int get() = min(10, value.toInt())

    val precise: Double get() = value

    /** 已过去的秒数 */
    val elapsedSec: Int
        get() = if (startedAtMs == 0L) 0 else ((System.currentTimeMillis() - startedAtMs) / 1000).toInt()

    /** 点「开始」：以 5 点为起点重新计时 */
    fun start() {
        value = 5.0
        phase = Phase.NORMAL
        startedAtMs = System.currentTimeMillis()
        lastTickMs = startedAtMs
        running = true
        hasStarted = true
    }

    fun pause() {
        settle()
        running = false
    }

    fun resume() {
        lastTickMs = System.currentTimeMillis()
        running = true
    }

    fun stop() {
        running = false
        value = 5.0
        phase = Phase.NORMAL
        startedAtMs = 0
        lastTickMs = 0
        hasStarted = false
    }

    /** 敌方出了一张牌 */
    fun onEnemyPlay(cost: Int, nowMs: Long = System.currentTimeMillis()) {
        if (!running) return
        settle(nowMs)
        value = (value - cost).coerceAtLeast(0.0)
    }

    /** 按官方时间轴推倍率 */
    private fun autoPhase(sec: Int): Phase = when {
        sec < 120 -> Phase.NORMAL       // 第 1、2 分钟
        sec < 240 -> Phase.DOUBLE       // 第 3 分钟 + 加时第 4 分钟
        else -> Phase.TRIPLE            // 加时第 5 分钟
    }

    private fun settle(nowMs: Long = System.currentTimeMillis()) {
        if (!running || lastTickMs == 0L) {
            lastTickMs = nowMs
            return
        }
        val auto = autoPhase(elapsedSec)
        if (auto != phase) phase = auto
        val dt = (nowMs - lastTickMs).coerceAtLeast(0)
        val perPoint = 2800.0 / phase.multiplier
        value = min(10.0, value + dt / perPoint)
        lastTickMs = nowMs
    }

    fun poll(): Int {
        settle()
        return display
    }
}