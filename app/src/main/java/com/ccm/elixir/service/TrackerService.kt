package com.ccm.elixir.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ccm.elixir.data.BattlePoller
import com.ccm.elixir.native.NativeRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class TrackerService : Service() {

    private lateinit var runner: NativeRunner
    private lateinit var poller: BattlePoller
    private val scope = CoroutineScope(SupervisorJob())

    private var overlay: OverlayHost? = null

    override fun onCreate() {
        super.onCreate()
        runner = NativeRunner(applicationContext)
        poller = BattlePoller(runner) { state -> overlay?.onState(state) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        if (overlay == null) {
            overlay = OverlayHost(applicationContext).also {
                it.bindTracker(poller.tracker)
                it.show()
            }
        }
        poller.resetBattle()
        poller.start(scope)

        return START_STICKY
    }

    override fun onDestroy() {
        poller.stop()
        overlay?.hide()
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val chId = "elixir_tracker"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(chId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(chId, "圣水记牌器", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("圣水记牌器")
            .setContentText("正在读取战斗数据")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(1001, n)
    }
}