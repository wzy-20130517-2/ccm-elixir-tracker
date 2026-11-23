package com.ccm.elixir.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccm.elixir.data.*
import kotlinx.coroutines.delay

/**
 * 悬浮窗根布局（全屏透明）。
 *
 * 布局：
 *  - 左上：敌方牌网格（已知彩色，未知灰色）
 *  - 中右：圣水球（实时敌方圣水）
 *  - 战场：出牌落点预判（卡片 + 准星，0.8s 淡出）
 */
@Composable
fun OverlayRoot(state: BattlePoller.State?, context: Context) {
    Box(Modifier.fillMaxSize()) {
        // 左上：敌方手牌
        EnemyHandPanel(
            known = state?.enemyKnown ?: emptyList(),
            context = context,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 90.dp)
        )

        // 中右：圣水
        ElixirOrb(
            value = state?.elixir ?: 0,
            real = state?.elixirReal ?: false,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .offset(y = 40.dp)
        )

        // 落点预判
        state?.newDeploys?.forEach { ev ->
            key(ev.atMs, ev.cardId, ev.wx, ev.wy) {
                DeployMarker(ev, context)
            }
        }
    }
}

/* ---------------- 敌方手牌 ---------------- */

@Composable
private fun EnemyHandPanel(known: List<Int>, context: Context, modifier: Modifier = Modifier) {
    // 已知牌按发现顺序，最多 8 张
    val items = remember(known) { known.take(8) }
    if (items.isEmpty()) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { idx, cardId ->
            EnemyCardSlot(cardId = cardId, context = context, isNext = idx == 4)
        }
    }
}

@Composable
private fun EnemyCardSlot(cardId: Int, context: Context, isNext: Boolean) {
    val card = CardDb.get(cardId)
    val w = if (isNext) 34.dp else 42.dp
    val h = w * 1.2f

    val art = remember(cardId, isNext) {
        CardArt.load(context, card?.png, 120)
    }

    Box(
        Modifier
            .size(w, h)
            .clip(RoundedCornerShape(5.dp))
            .background(T.surfaceHi)
            .border(1.dp, if (isNext) T.textDim.copy(alpha = 0.5f) else T.border, RoundedCornerShape(5.dp))
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = card?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/* ---------------- 圣水球 ---------------- */

@Composable
private fun ElixirOrb(value: Int, real: Boolean, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(value, tween(320), label = "elx")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // 外圈
            Canvas(Modifier.size(62.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(T.elixir.copy(alpha = 0.95f), T.accentSoft.copy(alpha = 0.85f))
                    ),
                    radius = size.minDimension / 2
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = size.minDimension / 2 - 2.dp.toPx(),
                    style = Stroke(1.5.dp.toPx())
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$animated", color = Color.White, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (real) "敌方" else "估算", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp
                )
            }
        }
    }
}

/* ---------------- 落点预判 ---------------- */

@Composable
private fun DeployMarker(ev: DeployEvent, context: Context) {
    var visible by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }

    LaunchedEffect(ev.atMs) {
        visible = true
        delay(800)
        visible = false
        delay(240)
        gone = true
    }
    if (gone) return

    val alpha by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(if (visible) 180 else 240), label = "a"
    )
    val scale by animateFloatAsState(
        if (visible) 1f else 0.72f,
        tween(if (visible) 220 else 240), label = "s"
    )

    // 世界坐标 -> 屏幕坐标（简化标定，实际按设备分辨率换算）
    val (sx, sy) = worldToScreen(ev.wx, ev.wy)
    val card = CardDb.get(ev.cardId)
    val art = remember(ev.cardId) { CardArt.load(context, card?.png, 140) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset(x = sx.dp - 26.dp, y = sy.dp - 52.dp)
                .alpha(alpha)
                .scale(scale)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 卡片
                Box(
                    Modifier
                        .size(52.dp, 62.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(T.surface)
                        .border(1.5.dp, T.accent, RoundedCornerShape(7.dp))
                ) {
                    if (art != null) {
                        Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Spacer(Modifier.height(3.dp))
                // 准星
                Crosshair(Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = T.accent
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(c.copy(alpha = 0.9f), r * 0.55f, style = Stroke(1.6.dp.toPx()))
        drawCircle(c.copy(alpha = 0.5f), r * 0.18f)
        // 四个短刻度
        val tick = r * 0.42f
        listOf(
            Offset(center.x, center.y - r) to Offset(center.x, center.y - r + tick),
            Offset(center.x, center.y + r) to Offset(center.x, center.y + r - tick),
            Offset(center.x - r, center.y) to Offset(center.x - r + tick, center.y),
            Offset(center.x + r, center.y) to Offset(center.x + r - tick, center.y),
        ).forEach { (a, b) -> drawLine(c, a, b, 1.6.dp.toPx()) }
    }
}

/**
 * 世界坐标 → 屏幕 dp。
 *
 * 世界 18000x32000；屏幕按 1280x2772 基准标定，再按密度折成 dp。
 * 这些常数来自 Termux 侧的实测标定（geo.py）。
 */
private fun worldToScreen(wx: Int, wy: Int): Pair<Float, Float> {
    val kx = 0.076f
    val ky = 0.0609f
    val cx = 667f
    val cy = 453f
    val w0x = 9000f
    val w0y = 3000f
    val px = cx + (wx - w0x) * kx
    val py = cy + (wy - w0y) * ky
    // px/py 是 1280x2772 下的像素，转 dp（密度约 2.75，取 0.38）
    return px * 0.38f to py * 0.38f
}