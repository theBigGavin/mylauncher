package com.mylauncher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

/** 设置面板:图标大小 / 字号滑杆(实时生效)、显示图标开关、恢复默认布局。 */
@Composable
fun SettingsPanel(
    iconSize: Int,
    fontSize: Int,
    showIcons: Boolean,
    onIconSize: (Int) -> Unit,
    onFontSize: (Int) -> Unit,
    onShowIcons: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val marginX = with(density) { 14.dp.roundToPx() }
    val marginBottom = with(density) { 64.dp.roundToPx() }

    val provider = remember(marginX, marginBottom) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset(
                x = (windowSize.width - popupContentSize.width - marginX).coerceAtLeast(0),
                y = (windowSize.height - popupContentSize.height - marginBottom).coerceAtLeast(0),
            )
        }
    }

    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(250.dp)
                .shadow(16.dp, RoundedCornerShape(16.dp))
                .background(Color(0xFFFAFAFC), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            BasicText(
                text = "显示设置",
                style = TextStyle(
                    color = Color(0xFF1E1E22),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(12.dp))
            SettingRow("图标大小") {
                MiniSlider(
                    value = iconSize.toFloat(),
                    range = 24f..56f,
                    modifier = Modifier.width(120.dp),
                    onChange = { onIconSize((it / 2).roundToInt() * 2) },
                )
            }
            SettingRow("字体大小") {
                MiniSlider(
                    value = fontSize.toFloat(),
                    range = 18f..40f,
                    modifier = Modifier.width(120.dp),
                    onChange = { onFontSize(it.roundToInt()) },
                )
            }
            SettingRow("显示图标") {
                MiniSwitch(checked = showIcons, onChange = onShowIcons)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF1E1E22))
                    .clickable(onClick = onReset)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "恢复默认布局",
                    style = TextStyle(color = Color.White, fontSize = 14.sp),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = Color(0xFF1E1E22), fontSize = 14.sp),
        )
        content()
    }
}

/** 极简自绘滑杆:轨道 + 滑块,支持点按与拖动。 */
@Composable
private fun MiniSlider(
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
            val track = Color(0x1F000000)
            val active = Color(0xFF1E1E22)
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(active, Offset(0f, y), Offset(size.width * frac, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(active, radius = 6.dp.toPx(), center = Offset(size.width * frac, y))
        }
    }
}

/** 极简自绘开关。 */
@Composable
private fun MiniSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val offset by animateDpAsState(targetValue = if (checked) 18.dp else 0.dp, label = "switch")
    Box(
        Modifier
            .width(40.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (checked) Color(0xFF1E1E22) else Color(0x1F000000))
            .clickable { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
