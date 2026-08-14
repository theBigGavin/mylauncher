package com.mylauncher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun MiniSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    // 拖动中旋钮放大(graphicsLayer 缩放,不占布局、不影响行高、不闪烁)
    val knobScale by animateFloatAsState(if (dragging) 1.7f else 1f, label = "sliderKnob")

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
                    dragging = true
                    currentOnChange(valueFor(down.position.x))
                    drag(down.id) { change ->
                        currentOnChange(valueFor(change.position.x))
                        change.consume()
                    }
                    dragging = false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val y = size.height / 2f
            val track = Color.White.copy(alpha = 0.20f)
            val active = Color.White
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(active, Offset(0f, y), Offset(size.width * frac, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(active, radius = 6.dp.toPx() * knobScale, center = Offset(size.width * frac, y))
        }
        // 拖动中的气泡数值(悬浮在旋钮上方,不占布局)
        if (dragging) {
            val label = value.roundToInt().toString()
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(LocalDensity.current) { (widthPx * frac - 18.dp.toPx()).toDp() },
                        y = (-30).dp,
                    )
                    .width(36.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = label,
                    style = TextStyle(
                        color = Color(0xFF1E1E22),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
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
