package com.ccm.elixir.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
 *  - 左上：敌方 8 张牌（固定 8 格，未知灰色）
 *  - 中右：圣水球 + 开始/暂停/倍率
 *  - 战场：出牌落点预判（卡片 + 准星，0.8s 淡出）
 */
@Composable
fun OverlayRoot(
    state: BattlePoller.State?,
    tracker: EnemyTracker?,
    elixir: ElixirTracker?,
    context: Context,
) {
    Box(Modifier.fillMaxSize()) {
        EnemyDeckPanel(
            tracker, context,
            Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 96.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .offset(y = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElixirOrb(state?.elixir ?: 0, elixir?.running == true, context)
            ElixirControls(elixir)
        }

        state?.newDeploys?.forEach { ev ->
            key(ev.atMs, ev.cardId, ev.wx, ev.wy) { DeployMarker(ev, context) }
        }
    }
}

/* ---------------- 敌方 8 张牌 ---------------- */

@Composable
private fun EnemyDeckPanel(tracker: EnemyTracker?, context: Context, modifier: Modifier = Modifier) {
    if (tracker == null) return
    val snap = tracker.playCountSnapshot()
    val deck = remember(snap) { tracker.deck() }
    val handSet = remember(snap) { tracker.hand().toSet() }
    val next = remember(snap) { tracker.nextCard() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(8) { i ->
            val cardId = deck.getOrNull(i)
            DeckSlot(
                cardId = cardId,
                isHand = cardId != null && cardId in handSet,
                isNext = cardId != null && cardId == next,
                context = context,
                trackerRef = tracker,
            )
        }
    }
}

@Composable
private fun DeckSlot(
    cardId: Int?,
    isHand: Boolean,
    isNext: Boolean,
    context: Context,
    trackerRef: EnemyTracker?,
) {
    val w = if (isNext) 30.dp else 36.dp
    val h = w * 1.2f
    val evolved = isHand && cardId != null && trackerRef?.showEvolved(cardId) == true

    val art: ImageBitmap? = remember(cardId, evolved) {
        if (cardId == null) null
        else {
            val c = CardDb.get(cardId)
            CardArt.load(context, if (evolved) (c?.pngEvo ?: c?.png) else c?.png, 110)
        }
    }

    Box(
        Modifier
            .size(w, h)
            .clip(RoundedCornerShape(4.dp))
            .background(if (cardId == null) T.surfaceHi.copy(alpha = 0.5f) else T.surface)
            .border(
                1.dp,
                when {
                    isNext -> T.gold.copy(alpha = 0.85f)
                    isHand -> T.border
                    else -> T.border.copy(alpha = 0.3f)
                },
                RoundedCornerShape(4.dp)
            )
    ) {
        if (art != null) {
            Image(
                bitmap = art, contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(if (isHand) 1f else 0.45f),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/* ---------------- 圣水 ---------------- */

@Composable
private fun ElixirOrb(value: Int, running: Boolean, context: Context) {
    val pop by animateFloatAsState(
        if (value >= 10) 1.07f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 900f), label = "pop"
    )
    val icon = remember { CardArt.loadIcon(context, "elixir_icon.png", 160) }

    Box(Modifier.scale(pop).alpha(if (running) 1f else 0.55f), contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, null, Modifier.size(62.dp))
        else Box(Modifier.size(62.dp).clip(RoundedCornerShape(percent = 50)).background(T.elixir))

        Text("$value", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(
            if (running) "敌" else "停",
            color = Color.White.copy(alpha = 0.85f), fontSize = 8.sp,
            modifier = Modifier.offset(y = 16.dp)
        )
    }
}

@Composable
private fun ElixirControls(elixir: ElixirTracker?) {
    if (elixir == null) return
    var running by remember { mutableStateOf(elixir.running) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // 开始 / 暂停
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (running) T.red.copy(alpha = 0.86f) else T.green.copy(alpha = 0.9f))
                .clickable {
                    if (running) { elixir.pause(); running = false }
                    else { if (elixir.precise <= 0.0 || !elixir.running) elixir.start(); else elixir.resume(); running = true }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(13.dp)) {
                if (running) {
                    drawRect(Color.White, topLeft = Offset(size.width * 0.18f, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.26f, size.height))
                    drawRect(Color.White, topLeft = Offset(size.width * 0.56f, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.26f, size.height))
                } else {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.22f, 0f)
                        lineTo(size.width * 0.85f, size.height / 2)
                        lineTo(size.width * 0.22f, size.height)
                        close()
                    }
                    drawPath(p, Color.White)
                }
            }
        }

        // 当前倍率（只读，按官方时间轴自动切）
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(T.surface.copy(alpha = 0.9f))
                .border(1.dp, T.border, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                "${elixir.phase.multiplier.toInt()}×",
                color = when (elixir.phase) {
                    ElixirTracker.Phase.NORMAL -> T.textDim
                    ElixirTracker.Phase.DOUBLE -> T.gold
                    ElixirTracker.Phase.TRIPLE -> T.accent
                },
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

/* ---------------- 落点预判 ---------------- */

@Composable
private fun DeployMarker(ev: DeployEvent, context: Context) {
    var phase by remember { mutableStateOf(0) }
    if (phase == 3) return

    LaunchedEffect(ev.atMs) {
        phase = 0
        delay(120); phase = 1
        delay(680); phase = 2
        delay(240); phase = 3
    }

    val alpha by animateFloatAsState(
        when (phase) { 0 -> 0f; 1 -> 1f; 2 -> 0f; else -> 0f },
        tween(if (phase == 2) 240 else 140), label = "a"
    )
    val scale by animateFloatAsState(
        when (phase) { 0 -> 0.6f; 1 -> 1f; 2 -> 0.9f; else -> 0.9f },
        tween(if (phase == 2) 240 else 200), label = "s"
    )

    val (sx, sy) = worldToScreen(ev.wx, ev.wy)
    val art = remember(ev.cardId, ev.evolved) {
        val c = CardDb.get(ev.cardId)
        CardArt.load(context, if (ev.evolved) (c?.pngEvo ?: c?.png) else c?.png, 140)
    }

    Box(
        Modifier
            .offset(x = sx.dp - 24.dp, y = sy.dp - 62.dp)
            .alpha(alpha)
            .scale(scale)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(48.dp, 58.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(T.surface)
                    .border(1.5.dp, T.accent, RoundedCornerShape(6.dp))
            ) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(2.dp))
            Crosshair(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = T.accent
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(c.copy(alpha = 0.92f), r * 0.5f, style = Stroke(1.6.dp.toPx()))
        val tick = r * 0.4f
        listOf(
            Offset(center.x, center.y - r) to Offset(center.x, center.y - r + tick),
            Offset(center.x, center.y + r) to Offset(center.x, center.y + r - tick),
            Offset(center.x - r, center.y) to Offset(center.x - r + tick, center.y),
            Offset(center.x + r, center.y) to Offset(center.x + r - tick, center.y),
        ).forEach { (a, b) -> drawLine(c, a, b, 1.6.dp.toPx()) }
    }
}

private fun worldToScreen(wx: Int, wy: Int): Pair<Float, Float> {
    val px = 667f + (wx - 9000f) * 0.076f
    val py = 453f + (wy - 3000f) * 0.0609f
    return px * 0.38f to py * 0.38f
}