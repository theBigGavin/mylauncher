package com.mylauncher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

@Composable
internal fun MiniSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    var widthPx by remember { mutableFloatStateOf(1f) }

    fun valueFor(x: Float): Float =
        range.start + (x / widthPx).coerceIn(0f, 1f) * (range.endInclusive - range.start)

    Box(
        modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(range) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    currentOnChange(valueFor(down.position.x))
                    drag(down.id) { change ->
                        currentOnChange(valueFor(change.position.x))
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val y = size.height / 2f
            val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val track = Color.White.copy(alpha = 0.20f)
            val active = Color.White
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(active, Offset(0f, y), Offset(size.width * frac, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(active, radius = 6.dp.toPx(), center = Offset(size.width * frac, y))
        }
    }
}

/** 极简自绘开关(壁纸深底风格:开=纯白轨道+深色圆钮,关=20% 白轨道+白圆钮)。 */
@Composable
internal fun MiniSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val offset by animateDpAsState(targetValue = if (checked) 18.dp else 0.dp, label = "switch")
    Box(
        Modifier
            .width(40.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (checked) Color.White else Color.White.copy(alpha = 0.20f))
            .clickable { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF1E1E22) else Color.White)
        )
    }
}
