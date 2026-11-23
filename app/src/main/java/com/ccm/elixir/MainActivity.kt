package com.ccm.elixir

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccm.elixir.service.TrackerService
import com.ccm.elixir.ui.T
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    onCheckOverlay = { hasOverlayPermission() },
                    onRequestOverlay = { requestOverlay() },
                    onOpenSettings = { openOverlaySettings() },
                    onStart = { startTracker() },
                    onStop = { stopTracker() },
                )
            }
        }
    }

    private fun hasOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true

    private fun requestOverlay() {
        if (hasOverlayPermission()) return
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun openOverlaySettings() = requestOverlay()

    private fun startTracker() {
        val i = Intent(this, TrackerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    }

    private fun stopTracker() {
        stopService(Intent(this, TrackerService::class.java))
    }
}

@Composable
private fun MainScreen(
    onCheckOverlay: () -> Boolean,
    onRequestOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var overlayOk by remember { mutableStateOf(onCheckOverlay()) }
    var running by remember { mutableStateOf(false) }
    var showEvo by remember { mutableStateOf(true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(T.bg, Color(0xFF0E1219), T.bg))
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header()

            PermissionCard(
                granted = overlayOk,
                onRequest = {
                    onRequestOverlay()
                },
                onRefresh = { overlayOk = onCheckOverlay() },
            )

            ToggleCard(
                running = running,
                ready = overlayOk,
                onToggle = {
                    if (!overlayOk) return@ToggleCard
                    running = !running
                    if (running) onStart() else onStop()
                },
            )

            SettingsCard(showEvo = showEvo, onShowEvoChange = { showEvo = it })

            InfoCard(showEvo)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Header() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ElixirDrop(36.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("圣水记牌器", color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("实时读内存 · 敌方手牌推断", color = T.textDim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ElixirDrop(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(Brush.verticalGradient(listOf(T.elixir, T.accentSoft)))
    )
}

@Composable
private fun PermissionCard(granted: Boolean, onRequest: () -> Unit, onRefresh: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = T.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (granted) T.green else T.red)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    if (granted) "悬浮窗权限已授予" else "需要悬浮窗权限",
                    color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
            }
            Text(
                if (granted) "可以开始记牌了" else "没有这个权限，浮层无法显示在游戏上方",
                color = T.textDim, fontSize = 13.sp
            )
            if (!granted) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("去授权", primary = true, onClick = onRequest)
                    Btn("已授权，刷新", primary = false, onClick = onRefresh)
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(running: Boolean, ready: Boolean, onToggle: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "a"
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = T.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (running) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(T.green.copy(alpha = pulse))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (running) "运行中" else "已停止",
                        color = if (running) T.green else T.text,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (ready) (if (running) "浮层已显示在屏幕上" else "点击开启，浮层会出现在游戏上方")
                    else "先授予悬浮窗权限",
                    color = T.textDim, fontSize = 12.sp
                )
            }
            Switch(
                checked = running,
                onCheckedChange = { onToggle() },
                enabled = ready,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = T.text,
                    checkedTrackColor = T.accent,
                    uncheckedThumbColor = T.textDim,
                    uncheckedTrackColor = T.surfaceHi,
                )
            )
        }
    }
}

@Composable
private fun SettingsCard(showEvo: Boolean, onShowEvoChange: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = T.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("显示设置", color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("觉醒卡用觉醒贴图", color = T.text, fontSize = 14.sp)
                    Text("关闭后统一用普通卡面", color = T.textDim, fontSize = 12.sp)
                }
                Switch(
                    checked = showEvo,
                    onCheckedChange = onShowEvoChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = T.text,
                        checkedTrackColor = T.accentSoft,
                        uncheckedThumbColor = T.textDim,
                        uncheckedTrackColor = T.surfaceHi,
                    )
                )
            }
        }
    }
}

@Composable
private fun InfoCard(showEvo: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = T.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("怎么用", color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Step(1, "授予悬浮窗权限")
            Step(2, "开启开关，浮层出现在游戏上方")
            Step(3, "进入战斗，自动识别敌方出的牌")
            Spacer(Modifier.height(4.dp))
            Text("说明", color = T.textDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                "· 敌方未知的牌显示为灰色\n" +
                        "· 已知的 4 张手牌正常显示，下一张略小\n" +
                        "· 觉醒卡在第 2 次出牌时换觉醒贴图\n" +
                        "· 精英卡整场保持精英外观",
                color = T.textDim, fontSize = 12.sp, lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(T.surfaceHi),
            contentAlignment = Alignment.Center
        ) {
            Text("$n", color = T.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = T.textDim, fontSize = 13.sp)
    }
}

@Composable
private fun Btn(text: String, primary: Boolean, onClick: () -> Unit) {
    if (primary) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = T.accent),
            shape = RoundedCornerShape(10.dp)
        ) { Text(text, fontSize = 13.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, T.border)
        ) { Text(text, color = T.textDim, fontSize = 13.sp) }
    }
}
