package com.ccm.elixir.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccm.elixir.data.ElixirTracker
import kotlin.math.roundToInt

/**
 * 控制窗：开始 / 暂停 + 收起/展开。
 *
 * 这可是能被触摸的独立窗口（显示层是 NOT_TOUCHABLE）。
 * 拖动按钮可移动整个窗口。
 */
@Composable
fun ControlPad(
    elixir: ElixirTracker?,
    onDrag: (Int, Int) -> Unit,
) {
    if (elixir == null) return
    var expanded by remember { mutableStateOf(true) }
    val running = elixir.running
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    val width by animateFloatAsState(if (expanded) 1f else 0.45f, tween(220), label = "w")

    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(T.surface.copy(alpha = 0.94f))
            .border(1.dp, T.border, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    dragX += drag.x
                    dragY += drag.y
                    onDrag(dragX.roundToInt(), dragY.roundToInt())
                }
            }
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 开始/暂停
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (running) T.red.copy(alpha = 0.9f) else T.green.copy(alpha = 0.9f))
                .clickable {
                    if (running) elixir.pause() else elixir.start()
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(13.dp)) {
                if (running) {
                    drawRect(Color.White, Offset(size.width * 0.16f, 0f), Size(size.width * 0.26f, size.height))
                    drawRect(Color.White, Offset(size.width * 0.58f, 0f), Size(size.width * 0.26f, size.height))
                } else {
                    Path().apply {
                        moveTo(size.width * 0.22f, 0f)
                        lineTo(size.width * 0.86f, size.height / 2)
                        lineTo(size.width * 0.22f, size.height)
                        close()
                    }.let { drawPath(it, Color.White) }
                }
            }
        }

        // 倍率（只读）
        if (expanded) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(T.surfaceHi)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
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

        // 展开/收起
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(9.dp)) {
                val p = Path().apply {
                    if (expanded) {
                        moveTo(0f, size.height * 0.35f); lineTo(size.width / 2, size.height * 0.75f)
                        lineTo(size.width, size.height * 0.35f)
                    } else {
                        moveTo(0f, size.height * 0.65f); lineTo(size.width / 2, size.height * 0.25f)
                        lineTo(size.width, size.height * 0.65f)
                    }
                }
                drawPath(p, T.textDim)
            }
        }
    }
}
