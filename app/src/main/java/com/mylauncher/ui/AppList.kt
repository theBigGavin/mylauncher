package com.mylauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mylauncher.data.AppEntry
import com.mylauncher.data.HomeStore
import com.mylauncher.icons.rememberMonoIcon
import kotlin.math.roundToInt

/** 主屏条目(已解析):真实 App + 可选自定义名。 */
data class HomeItem(val app: AppEntry, val customName: String?) {
    val displayName: String get() = customName ?: app.label
}

private enum class PressOutcome { Tap, LongPress, Cancelled }

/**
 * 区分 点击 / 长按 / 滑动:
 * 在长按时限内松手且未越过 touch slop = Tap;越过 slop = Cancelled(交给列表滚动);超时 = LongPress。
 */
private suspend fun AwaitPointerEventScope.awaitPressOutcome(down: PointerInputChange): PressOutcome {
    var moved = false
    // 注意:这里调用的是 AwaitPointerEventScope 自带的 withTimeoutOrNull,
    // 其超时抛 PointerEventTimeoutCancellationException(非 kotlinx 的),因此用 OrNull 变体避免异常类型耦合。
    val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var finished = false
        while (!finished) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            for (change in event.changes) {
                if (change.id != down.id) continue
                if (change.changedToUp()) {
                    finished = true
                    break
                }
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                    finished = true
                    break
                }
            }
        }
        if (moved) PressOutcome.Cancelled else PressOutcome.Tap
    }
    return result ?: PressOutcome.LongPress
}

/** 行高:max(图标, 字号*1.2) + 上下各 10dp 内边距。 */
@Composable
fun appRowHeight(iconSize: Dp, fontSize: TextUnit): Dp {
    val density = LocalDensity.current
    val textHeight = with(density) { (fontSize * 1.2f).toDp() }
    return maxOf(iconSize, textHeight) + 20.dp
}

@Composable
private fun IconBox(icon: ImageBitmap?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(24))
            .background(Color.White.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * 单行:白色 900 字重名称 + 单色化图标。
 * 竖屏:图标在左、名称在右;横屏:整行靠右,图标固定一列,名称固定 5.5em 宽右对齐。
 */
@Composable
fun AppRow(
    name: String,
    icon: ImageBitmap?,
    iconSize: Dp,
    fontSize: TextUnit,
    showIcons: Boolean,
    landscape: Boolean,
    modifier: Modifier = Modifier,
    nameColor: Color = Color.White,
    nameWeight: FontWeight = FontWeight.Black,
) {
    if (landscape) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (showIcons) {
                IconBox(icon, iconSize)
                Spacer(Modifier.width(18.dp))
            }
            val nameWidth = with(LocalDensity.current) { (fontSize * 5.5f).toDp() }
            BasicText(
                text = name,
                modifier = Modifier.width(nameWidth),
                style = TextStyle(
                    color = nameColor,
                    fontSize = fontSize,
                    fontWeight = nameWeight,
                    letterSpacing = 0.5.sp,
                    lineHeight = fontSize * 1.1f,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcons) {
                IconBox(icon, iconSize)
                Spacer(Modifier.width(16.dp))
            }
            BasicText(
                text = name,
                style = TextStyle(
                    color = nameColor,
                    fontSize = fontSize,
                    fontWeight = nameWeight,
                    letterSpacing = 0.5.sp,
                    lineHeight = fontSize * 1.1f,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "＋ 添加应用" 行:虚线框 + 加号,细体白色 70% 名称。 */
@Composable
private fun AddRow(
    iconSize: Dp,
    fontSize: TextUnit,
    showIcons: Boolean,
    landscape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        if (landscape) {
            Spacer(Modifier.weight(1f))
        }
        if (showIcons) {
            val dashColor = Color.White.copy(alpha = 0.5f)
            val plusColor = Color.White.copy(alpha = 0.7f)
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(24))
                    .drawBehind {
                        drawRoundRect(
                            color = dashColor,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                            ),
                            cornerRadius = CornerRadius(size.minDimension * 0.24f),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(iconSize * 0.5f)) {
                    val sw = maxOf(1.5f, size.minDimension * 0.08f)
                    drawLine(plusColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = sw)
                    drawLine(plusColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = sw)
                }
            }
            Spacer(Modifier.width(if (landscape) 18.dp else 16.dp))
        }
        BasicText(
            text = "添加应用",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.7f),
                fontSize = fontSize,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.5.sp,
            ),
            maxLines = 1,
        )
    }
}

/**
 * 主屏 App 列表。
 * 手势:点击启动;长按静止松手 = 弹菜单;长按后拖动 = 拖拽排序(自实现)。
 */
@Composable
fun AppList(
    items: List<HomeItem>,
    iconSize: Dp,
    fontSize: TextUnit,
    showIcons: Boolean,
    landscape: Boolean,
    onLaunch: (HomeItem) -> Unit,
    onLongPressMenu: (index: Int, positionInWindow: Offset) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rowHeight = appRowHeight(iconSize, fontSize)
    val rowHeightPx = with(density) { rowHeight.toPx() }

    // pointerInput 手势闭包不会因数据变化而重启,必须经 rememberUpdatedState 取最新值,
    // 否则拖拽/点击会读到过期列表(例如改名后的自定义名丢失)。
    val currentItems by rememberUpdatedState(items)
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnLongPressMenu by rememberUpdatedState(onLongPressMenu)
    val currentOnMove by rememberUpdatedState(onMove)

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val targetIndex = if (dragIndex in items.indices) {
        (dragIndex + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, items.lastIndex)
    } else {
        -1
    }

    LazyColumn(
        modifier = modifier,
        userScrollEnabled = dragIndex < 0,
    ) {
        itemsIndexed(items, key = { _, it -> it.app.component }) { index, item ->
            val shiftTarget = when {
                dragIndex < 0 || index == dragIndex || targetIndex < 0 -> 0f
                dragIndex < targetIndex && index in (dragIndex + 1)..targetIndex -> -rowHeightPx
                dragIndex > targetIndex && index in targetIndex until dragIndex -> rowHeightPx
                else -> 0f
            }
            val shift by animateFloatAsState(targetValue = shiftTarget, label = "rowShift")
            var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (index == dragIndex) 1f else 0f)
                    .graphicsLayer { translationY = if (index == dragIndex) dragOffset else shift }
                    .onGloballyPositioned { coords = it }
                    .pointerInput(item.app.component, index) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            when (awaitPressOutcome(down)) {
                                PressOutcome.Tap -> currentOnLaunch(item)
                                PressOutcome.Cancelled -> Unit
                                PressOutcome.LongPress -> {
                                    val slopReached =
                                        awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                            change.consume()
                                        }
                                    if (slopReached == null) {
                                        // 长按静止松手 -> 弹菜单
                                        val pos = coords?.localToWindow(down.position) ?: Offset.Zero
                                        currentOnLongPressMenu(index, pos)
                                    } else {
                                        // 长按后拖动 -> 拖拽排序
                                        dragIndex = index
                                        dragOffset = 0f
                                        drag(slopReached.id) { change ->
                                            dragOffset += change.positionChange().y
                                            change.consume()
                                        }
                                        // 直接按当前累积偏移计算目标位(避免读到过期组合值)
                                        val to = (index + (dragOffset / rowHeightPx).roundToInt())
                                            .coerceIn(0, currentItems.lastIndex)
                                        dragIndex = -1
                                        dragOffset = 0f
                                        if (to != index) currentOnMove(index, to)
                                    }
                                }
                            }
                        }
                    },
            ) {
                val icon = rememberMonoIcon(item.app, iconSize)
                AppRow(
                    name = item.displayName,
                    icon = icon,
                    iconSize = iconSize,
                    fontSize = fontSize,
                    showIcons = showIcons,
                    landscape = landscape,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (landscape) 0.dp else 26.dp),
                )
            }
        }

        if (items.size < HomeStore.MAX_APPS) {
            item(key = "__add__") {
                AddRow(
                    iconSize = iconSize,
                    fontSize = fontSize,
                    showIcons = showIcons,
                    landscape = landscape,
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(horizontal = if (landscape) 0.dp else 26.dp),
                )
            }
        }
    }
}
