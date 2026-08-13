package com.mylauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 所有列表界面的左右边距:桌面 / 抽屉 / 选择器 / 设置页保持一致。 */
val LIST_H_MARGIN = 80.dp

/** 左滑露出的操作按钮总宽(修改名称 + 移除)。 */
internal val ACTION_WIDTH = 176.dp

/** 主屏条目(已解析):真实 App + 可选自定义名。 */
data class HomeItem(val app: AppEntry, val customName: String?) {
    val displayName: String get() = customName ?: app.label
}

internal enum class PressOutcome { Tap, LongPress, SwipeLeft, SwipeRight, Cancelled }

/**
 * 区分 点击 / 长按 / 左右滑 / 上下滑:
 * - 长按时限内松手且未越过 slop = Tap;垂直越过 slop = Cancelled(交给列表滚动)
 * - 水平越过 slop(且水平分量占优)= SwipeLeft / SwipeRight(左滑露操作)
 * - 超时未动 = LongPress(替换应用)
 */
internal suspend fun AwaitPointerEventScope.awaitPressOutcome(down: PointerInputChange): PressOutcome {
    val slop = viewConfiguration.touchSlop
    // 注意:这是 AwaitPointerEventScope 自带的 withTimeoutOrNull,
    // 超时抛 PointerEventTimeoutCancellationException(非 kotlinx 的),因此用 OrNull 变体。
    val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var finished = false
        var outcome = PressOutcome.Tap
        while (!finished) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            for (change in event.changes) {
                if (change.id != down.id) continue
                if (change.changedToUp()) {
                    finished = true
                    break
                }
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (abs(dx) > slop && abs(dx) > abs(dy)) {
                    outcome = if (dx < 0) PressOutcome.SwipeLeft else PressOutcome.SwipeRight
                    finished = true
                    break
                }
                if (abs(dy) > slop) {
                    outcome = PressOutcome.Cancelled
                    finished = true
                    break
                }
            }
        }
        outcome
    }
    return result ?: PressOutcome.LongPress
}

/** 行高:max(图标, 字号*1.2) + 上下各 10dp 内边距 + 行距。 */
@Composable
fun appRowHeight(iconSize: Dp, fontSize: TextUnit, spacing: Dp = 0.dp): Dp {
    val density = LocalDensity.current
    val textHeight = with(density) { (fontSize * 1.2f).toDp() }
    return maxOf(iconSize, textHeight) + 20.dp + spacing
}

@Composable
internal fun IconBox(icon: ImageBitmap?, size: Dp, badgeCount: Int) {
    // 外层不裁剪:角标要探出图标右上角,不能被圆角/边界裁掉
    Box(modifier = Modifier.size(size)) {
        // 图标层:圆角 + 阴影 + 背景(裁剪只影响这一层)
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(24),
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                )
                .clip(RoundedCornerShape(24))
                .background(Color.White.copy(alpha = 0.14f)),
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
        // 通知角标:红色圆点,贴在图标右上角(外层子元素,不被裁剪)
        if (badgeCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3D00)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 9.sp,
                    ),
                )
            }
        }
    }
}

/** 白字深色光晕:让文字从壁纸亮线里"立"出来。 */
internal val textShadow = Shadow(
    color = Color.Black.copy(alpha = 0.5f),
    offset = Offset(0f, 2f),
    blurRadius = 6f,
)

/**
 * 单行:白色 900 字重名称 + 单色化图标。
 * 不再强制占满行宽 —— 由调用方决定对齐与占位(手势区域 = 内容本身)。
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
    badgeCount: Int = 0,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcons) {
            IconBox(icon, iconSize, badgeCount)
            Spacer(Modifier.width(if (landscape) 18.dp else 16.dp))
        }
        BasicText(
            text = name,
            style = TextStyle(
                color = nameColor,
                fontSize = fontSize,
                fontWeight = nameWeight,
                letterSpacing = 0.5.sp,
                lineHeight = fontSize * 1.1f,
                textAlign = if (landscape) TextAlign.End else TextAlign.Start,
                shadow = textShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (landscape) Modifier.width(with(LocalDensity.current) { (fontSize * 5.5f).toDp() }) else Modifier,
        )
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
                textAlign = if (landscape) TextAlign.End else TextAlign.Start,
                shadow = textShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (landscape) Modifier.width(with(LocalDensity.current) { (fontSize * 5.5f).toDp() }) else Modifier,
        )
    }
}

/** 左滑露出的操作按钮(简单文本按钮,靠行背景提供可读性)。 */
@Composable
internal fun RevealButton(
    label: String,
    fg: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .width(ACTION_WIDTH / 2)
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                shadow = textShadow,
            ),
        )
    }
}

/**
 * 主屏 App 列表。
 * 手势(只挂在图标+名称内容上,不占整行):
 * 点击 → 启动;长按 → 替换应用;按住左滑 → iOS 式滑出"修改名称 / 移除"。
 */
@Composable
fun AppList(
    items: List<HomeItem>,
    iconSize: Dp,
    fontSize: TextUnit,
    rowSpacing: Dp,
    showIcons: Boolean,
    showBadges: Boolean,
    badgeCounts: Map<String, Int>,
    landscape: Boolean,
    onLaunch: (HomeItem) -> Unit,
    onReplace: (index: Int) -> Unit,
    onRename: (index: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onHoldChange: (Boolean) -> Unit = {},
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rowHeight = appRowHeight(iconSize, fontSize, rowSpacing)
    val actionWidthPx = with(density) { ACTION_WIDTH.toPx() }
    val rowHeightPx = with(density) { rowHeight.toPx() }
    // 拖动排序:被拖行索引 + 实时目标行(其间各行让位一行)
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragTarget by remember { mutableIntStateOf(-1) }

    // 单行展开状态:只有该行左滑,其余行与列都保持原位
    var revealedIndex by remember { mutableIntStateOf(-1) }
    fun setRevealed(i: Int) { revealedIndex = i }

    // pointerInput 手势闭包不会因数据变化而重启,必须经 rememberUpdatedState 取最新值
    val currentItems by rememberUpdatedState(items)
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnReplace by rememberUpdatedState(onReplace)
    val currentOnRename by rememberUpdatedState(onRename)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnHoldChange by rememberUpdatedState(onHoldChange)

    // 普通 Column + verticalScroll(不用 LazyColumn):左滑时行内容滑出列边界,
    // LazyColumn 会裁剪边界外的内容("APP被列表框遮挡消失"的根因);scrollable 不裁剪,可滚动。
    val scrollState = rememberScrollState()
    // 列表视口(window 坐标),拖拽排序边缘自动滚动用
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        modifier = modifier
            .onGloballyPositioned { listBounds = it.boundsInWindow() }
            .verticalScroll(scrollState),
    ) {
        items.forEachIndexed { index, item ->
            val revealed = index == revealedIndex
            val currentRevealed by rememberUpdatedState(revealed)
            // 内容左移量(px):左滑拖拽跟手,松手按阈值落定;只有本行动,其余行原位
            val shift = remember { Animatable(0f) }
            val buttonsAlpha = (shift.value / actionWidthPx).coerceIn(0f, 1f)
            // 拖动排序时被拖行的上浮量(px)
            val lift = remember { Animatable(0f) }
            // 拖动排序:被拖行之外、位于 [dragIndex, dragTarget] 之间的行让位一行
            val shiftTarget = if (dragIndex >= 0 && index != dragIndex && dragTarget >= 0) {
                when {
                    dragTarget > dragIndex && index in (dragIndex + 1)..dragTarget -> rowHeight
                    dragTarget < dragIndex && index in dragTarget until dragIndex -> -rowHeight
                    else -> 0.dp
                }
            } else 0.dp
            val shiftAnim by animateDpAsState(shiftTarget, label = "reorderShift")
            // 选中行背景:让用户看出当前行处于"管理"/"拖动"状态
            val rowBgAlpha by animateFloatAsState(
                targetValue = if (revealed) 0.12f
                else if (buttonsAlpha > 0f) 0.08f
                else if (lift.value != 0f) 0.10f
                else 0f,
                label = "revealBg",
            )
            // Material 水波纹:手势行手动发射按压交互(触点 = 手指位置)
            val interactionSource = remember { MutableInteractionSource() }
            var contentBounds by remember { mutableStateOf(Rect.Zero) }
            val scope = rememberCoroutineScope()
            // 展开状态变化时内容滑回/滑到位(点按钮关闭、滑右关闭等路径统一在这里回位)
            LaunchedEffect(revealed) {
                shift.animateTo(if (revealed) actionWidthPx else 0f)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (lift.value != 0f) 1f else 0f)
                    .offset(y = shiftAnim)
                    .graphicsLayer {
                        translationY = lift.value
                        scaleX = if (lift.value != 0f) 1.02f else 1f
                        scaleY = if (lift.value != 0f) 1.02f else 1f
                    }
            ) {
                // 选中行背景:覆盖 滑出后的内容 + 操作按钮(横屏靠右;竖屏整行)
                // requiredWidth:背景条要探出行/列表边界盖住滑出的内容,width 会被父约束钳制
                if (landscape) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .requiredWidth(with(density) { (contentBounds.width + ACTION_WIDTH.toPx() + 12.dp.toPx()).toDp() })
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = rowBgAlpha))
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = rowBgAlpha))
                    )
                }
                // 操作按钮层:行右侧,内容左滑后露出(自右滑入 + 淡入)
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .height(rowHeight)
                        .graphicsLayer {
                            alpha = buttonsAlpha
                            translationX = (1f - buttonsAlpha) * with(density) { 20.dp.toPx() }
                        },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RevealButton(
                        label = "修改名称",
                        fg = Color.White,
                        enabled = revealed,
                        onClick = {
                            setRevealed(-1)
                            currentOnRename(index)
                        },
                    )
                    RevealButton(
                        label = "移除",
                        fg = Color(0xFFFF8A80),
                        enabled = revealed,
                        onClick = {
                            setRevealed(-1)
                            currentOnRemove(index)
                        },
                    )
                }
                // 内容层:整行承载手势与 Material 水波纹,但只有落在图标+名称范围才算有效触控
                // 左滑时内容整体左移(只有本行);触控坐标跟随内容平移,命中检测不变
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (landscape) 12.dp else LIST_H_MARGIN)
                        .graphicsLayer { translationX = -buttonsAlpha * actionWidthPx }
                        .indication(interactionSource, LocalIndication.current)
                        .pointerInput(item.app.component, index) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // 触控区 = 图标+名称(行空白处不响应,避免误触)
                                // 左滑时内容整体左移:命中区跟随内容位移(两种坐标系都覆盖)
                                val shifted = contentBounds.translate(-shift.value, 0f)
                                if (!contentBounds.contains(down.position) &&
                                    !shifted.contains(down.position)
                                ) {
                                    return@awaitEachGesture
                                }
                                val press = PressInteraction.Press(down.position)
                                scope.launch { interactionSource.emit(press) }
                                when (val outcome = awaitPressOutcome(down)) {
                                    PressOutcome.Tap -> {
                                        scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                        if (currentRevealed) setRevealed(-1)
                                        else currentOnLaunch(item)
                                    }
                                    PressOutcome.LongPress -> {
                                        if (currentRevealed) {
                                            scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                            setRevealed(-1)
                                        } else {
                                            // 长按后:按住并上下拖动 → 拖动排序;不动直接松手 → 替换应用
                                            // hold 标志立即通知上层:长按已被行接管,壁纸的"长按开设置"不再触发
                                            currentOnHoldChange(true)
                                            try {
                                                var reorder = false
                                                var pointerYInList = Float.NaN
                                                var autoScrollJob: Job? = null
                                                val slop = viewConfiguration.touchSlop
                                                val edgePx = 56.dp.toPx()
                                                val maxStepPx = 12.dp.toPx()
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                                    var up = false
                                                    for (change in event.changes) {
                                                        if (change.id != down.id) continue
                                                        if (change.changedToUp()) { up = true; break }
                                                        // 长按后的移动全部归行处理,不落到壁纸手势(避免误开抽屉/设置)
                                                        change.consume()
                                                        if (!reorder && abs(change.position.y - down.position.y) > slop * 2f) {
                                                            reorder = true
                                                            scope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                                                            dragIndex = index
                                                            dragTarget = index
                                                            autoScrollJob = scope.launch {
                                                                while (true) {
                                                                    val y = pointerYInList
                                                                    val h = listBounds.height.toFloat()
                                                                    if (!y.isNaN() && h > 0f) {
                                                                        val delta = when {
                                                                            y < edgePx -> -maxStepPx * (1f - y / edgePx)
                                                                            y > h - edgePx -> maxStepPx * (1f - (h - y) / edgePx)
                                                                            else -> 0f
                                                                        }
                                                                        if (delta != 0f) {
                                                                            scrollState.dispatchRawDelta(delta)
                                                                        }
                                                                    }
                                                                    withFrameNanos { }
                                                                }
                                                            }
                                                        }
                                                        if (reorder) {
                                                            // 注意:position 坐标系会被本行的 graphicsLayer(lift)
                                                            // 与列表滚动一起平移 —— 必须加回 lift 才是真实跟手位移,
                                                            // 否则 dy 变成差分(行只跟手一半,drop 目标位计算错误)
                                                            val dy = change.position.y - down.position.y + lift.value
                                                            // 手指相对列表视口顶部的 y(行视口偏移 + 行内真实偏移),
                                                            // 供边缘自动滚动判定
                                                            pointerYInList = (index * rowHeightPx - scrollState.value) +
                                                                (change.position.y + lift.value)
                                                            scope.launch { lift.snapTo(dy) }
                                                            dragTarget = (index + (dy / rowHeightPx).roundToInt())
                                                                .coerceIn(0, currentItems.size - 1)
                                                        }
                                                    }
                                                    if (up) break
                                                }
                                                autoScrollJob?.cancel()
                                                if (reorder) {
                                                    val finalTarget = dragTarget
                                                    scope.launch { lift.animateTo(0f) }
                                                    dragIndex = -1
                                                    dragTarget = -1
                                                    if (finalTarget != index) currentOnReorder(index, finalTarget)
                                                } else {
                                                    scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                                    currentOnReplace(index)
                                                }
                                            } finally {
                                                currentOnHoldChange(false)
                                            }
                                        }
                                    }
                                    PressOutcome.SwipeLeft -> {
                                        if (!currentRevealed) {
                                            // 跟手拖动(内容跟随),松手按阈值落定
                                            var dx = 0f
                                            drag(down.id) { change ->
                                                dx += change.positionChange().x
                                                scope.launch { shift.snapTo((-dx).coerceIn(0f, actionWidthPx)) }
                                                change.consume()
                                            }
                                            scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                            if (dx < -actionWidthPx * 0.3f) {
                                                setRevealed(index)
                                                scope.launch { shift.animateTo(actionWidthPx) }
                                            } else {
                                                scope.launch { shift.animateTo(0f) }
                                            }
                                        }
                                    }
                                    PressOutcome.SwipeRight -> {
                                        scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                        setRevealed(-1)
                                    }
                                    PressOutcome.Cancelled -> {
                                        scope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                                    }
                                }
                            }
                        },
                    contentAlignment = if (landscape) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Row(
                        Modifier
                            .onGloballyPositioned { contentBounds = it.boundsInParent() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showIcons) {
                            IconBox(
                                icon = rememberMonoIcon(item.app, iconSize),
                                size = iconSize,
                                badgeCount = if (showBadges) (badgeCounts[item.app.packageName] ?: 0) else 0,
                            )
                            Spacer(Modifier.width(if (landscape) 18.dp else 16.dp))
                        }
                        BasicText(
                            text = item.displayName,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                lineHeight = fontSize * 1.1f,
                                textAlign = if (landscape) TextAlign.End else TextAlign.Start,
                                shadow = textShadow,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (landscape) Modifier.width(with(LocalDensity.current) { (fontSize * 5.5f).toDp() }) else Modifier,
                        )
                    }
                }
            }
        }

        if (items.size < HomeStore.MAX_APPS) {
            AddRow(
                iconSize = iconSize,
                fontSize = fontSize,
                showIcons = showIcons,
                landscape = landscape,
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    // 横屏与普通行一致用 12dp;竖屏用统一边距 LIST_H_MARGIN
                    // (横屏曾误用 80dp 导致"添加应用"名称被裁掉)
                    .padding(horizontal = if (landscape) 12.dp else LIST_H_MARGIN),
            )
        }
    }
}
