package com.mylauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.indication
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
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
import com.mylauncher.icons.rememberColorIcon
import com.mylauncher.icons.rememberMonoIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 所有列表界面的左右边距:桌面 / 抽屉 / 选择器 / 设置页保持一致。 */
val LIST_H_MARGIN = 80.dp

/** 竖屏主屏行的左右边距:比通用边距窄 1/3(触控区更宽,触发位置不局促);抽屉/选择器共用。 */
internal val PORTRAIT_ROW_MARGIN = LIST_H_MARGIN * 2 / 3

/** 左滑露出的操作按钮总宽(修改名称 + 移除)基线值。 */
internal val ACTION_WIDTH = 176.dp

/**
 * 左滑操作按钮的实际总宽:窄屏(手机竖屏)缩短,避免内容被挤出屏幕。
 * 宽屏(平板/横屏)保持 176dp。
 */
@Composable
internal fun actionWidth(): Dp =
    if (LocalConfiguration.current.screenWidthDp < 600) 112.dp else ACTION_WIDTH

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
    var maxDelta = 0f
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
                maxDelta = maxOf(maxDelta, maxOf(abs(dx), abs(dy)))
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
    // 超时判长按前先看手指是否动过:慢速滚动时 sub-slop 位移会持续整个长按窗口,
    // 若仍判长按会误触发行内动作(抽屉行直接启动应用)—— 有明显位移按滚动意图取消。
    // 真正的长按手指静止,抖动远小于 0.4 倍 slop。
    return result ?: if (maxDelta > slop * 0.4f) PressOutcome.Cancelled else PressOutcome.LongPress
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
        // 无底衬、无阴影:纯白剪影 glyph 直接画在壁纸上(不再透底的半透明圆角块);
        // clip 仅让 alpha 全满的兜底图标保持圆角方块外形
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(24)),
            )
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

/** 通知角标圆点:红底圆 + 白色数字(99+ 截断),与 IconBox 角标同款视觉。 */
@Composable
internal fun BadgeDot(badgeCount: Int, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    if (badgeCount <= 0) return
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFFF3D00)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = if (badgeCount > 99) "99+" else "$badgeCount",
            style = TextStyle(
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 8.sp,
            ),
        )
    }
}

/** 白字深色光晕:让文字从壁纸亮线里"立"出来。 */
internal val textShadow = Shadow(
    color = Color.Black.copy(alpha = 0.5f),
    offset = Offset(0f, 2f),
    blurRadius = 6f,
)

/**
 * 名称角标的垂直偏移(相对名称行框顶部,向下为正):贴最后一个字母的视觉顶部。
 * Roboto 度量近似:小写 x-height ≈ 0.40em 低于行框顶,大写/数字 ≈ 0.23em,CJK 近顶 ≈ 0.08em;
 * 再上收 8dp 让圆点轻叠在字母角上(用户微调:4dp→6dp→8dp)。
 */
@Composable
internal fun badgeTopShiftDp(fontSize: TextUnit, name: String): Dp {
    val em = when (name.lastOrNull()) {
        in 'a'..'z' -> 0.40f
        in 'A'..'Z', in '0'..'9' -> 0.23f
        else -> 0.08f
    }
    val density = LocalDensity.current
    return with(density) { (fontSize.toPx() * em - 8.dp.toPx()).toDp() }
}

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
        // 关闭图标显示时,通知角标叠在名称右上角(所有地方一致)——气泡不随图标一起消失
        Box {
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
            if (!showIcons && badgeCount > 0) {
                BadgeDot(
                    badgeCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = badgeTopShiftDp(fontSize, name)),
                )
            }
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
                textAlign = if (landscape) TextAlign.End else TextAlign.Start,
                shadow = textShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 行左滑露出的文字操作(抽屉行与桌面行共用)。 */
internal data class RowAction(
    val label: String,
    val onClick: (AppEntry) -> Unit,
    val destructive: Boolean = false,
)

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
        // 未启用时完全不挂 clickable:禁用态 clickable 仍会消费按下,挡住下面行的滑动手势(抽屉右侧区域滑不动)
        modifier
            .height(48.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
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
 * 右缘操作按钮条:淡入 + 自右滑入(未展开时整体透明,不参与命中)。
 * 桌面行与抽屉行共用;modifier 供调用方追加定位/留白(如抽屉的 12dp 右留白)。
 */
@Composable
internal fun BoxScope.RevealButtonBar(
    actions: List<RowAction>,
    alpha: Float,
    slidePx: Float,
    rowHeight: Dp,
    enabled: Boolean,
    buttonWidth: Dp,
    modifier: Modifier = Modifier,
    onAction: (RowAction) -> Unit,
) {
    Row(
        modifier
            .align(Alignment.CenterEnd)
            .height(rowHeight)
            .graphicsLayer {
                this.alpha = alpha
                translationX = (1f - alpha) * slidePx
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            RevealButton(
                label = action.label,
                fg = if (action.destructive) Color(0xFFFF8A80) else Color.White,
                enabled = enabled,
                modifier = Modifier.width(buttonWidth),
                onClick = { onAction(action) },
            )
        }
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
    showOriginalColor: Boolean,
    showBadges: Boolean,
    badgeCounts: Map<String, Int>,
    /** 桌面槽位数上限:列表满员后隐藏"添加应用"行。 */
    maxApps: Int,
    landscape: Boolean,
    onLaunch: (HomeItem) -> Unit,
    onReplace: (index: Int) -> Unit,
    onRename: (index: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    onHoldChange: (Boolean) -> Unit = {},
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rowHeight = appRowHeight(iconSize, fontSize, rowSpacing)
    val actionWidthPx = with(density) { actionWidth().toPx() }
    val rowHeightPx = with(density) { rowHeight.toPx() }

    // 单行展开状态:只有该行左滑,其余行与列都保持原位
    var revealedIndex by remember { mutableIntStateOf(-1) }
    fun setRevealed(i: Int) { revealedIndex = i }
    // 展开后无操作超时自动收起(点按钮/再滑右都会重置计时)
    LaunchedEffect(revealedIndex) {
        if (revealedIndex >= 0) {
            delay(3000)
            revealedIndex = -1
        }
    }

    // pointerInput 手势闭包不会因数据变化而重启,必须经 rememberUpdatedState 取最新值
    val currentItems by rememberUpdatedState(items)
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnReplace by rememberUpdatedState(onReplace)
    val currentOnRename by rememberUpdatedState(onRename)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnHoldChange by rememberUpdatedState(onHoldChange)

    // LazyColumn:虚拟化 + 原生滚动;列宽已含 ACTION_WIDTH(左滑露出区),
    // 滑出的内容仍在视口内,不会被裁剪
    val scrollState = rememberLazyListState()
    // "添加应用"按钮:默认隐藏;列表为空时恒显;拖动列表(即使不产生滚动)时临时出现,
    // 超时自动隐藏 —— 点击已注册的拖动次数,每次拖动重置计时
    // "添加应用"行常驻列表(槽位未满时),只做视觉显隐(alpha 淡入淡出),不插删 —— 布局稳定不跳。
    // 拖动列表时显示;松手后重新计时,3s 无操作淡出;列表为空时恒显
    var addRowShown by remember { mutableStateOf(items.isEmpty()) }
    var addRowHideTick by remember { mutableIntStateOf(0) }
    // 拖拽中禁止隐藏:3s 计时器到期时若手指仍在拖,跳过本次隐藏
    // (快速连续滚动时,上一次松手的计时会在本次拖拽中途到期,行会半路闪掉;
    // 且到期后 hideTick++ 重启本效果时 addRowShown 已为 false,不再重新排程 —— 修过的坑)
    var addRowDragActive by remember { mutableStateOf(false) }
    LaunchedEffect(addRowHideTick, items.isEmpty()) {
        if (addRowShown && items.isNotEmpty()) {
            delay(3000)
            if (!addRowDragActive) addRowShown = false
        }
    }
    LazyColumn(
        modifier = modifier
            // 只观察不消费:任何纵向拖动(含不滚动的拖)都临时显示"添加应用"
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragged = false
                    while (true) {
                        // 空闲超时兜底:按下后若 up 被中途打开的浮层吃掉(浮层遮挡命中路径,
                        // 本观察器收不到该 up),不能永远卡在等 up —— 1.5s 无事件视同手势结束,
                        // 否则添加应用行从此再也拉不出来(修过的坑)
                        val event = withTimeoutOrNull(1500) {
                            awaitPointerEvent(PointerEventPass.Main)
                        } ?: break
                        var up = false
                        for (change in event.changes) {
                            if (change.id != down.id) continue
                            if (change.changedToUp()) { up = true; break }
                            if (!dragged &&
                                abs(change.position.y - down.position.y) > viewConfiguration.touchSlop * 2f
                            ) {
                                dragged = true
                                addRowShown = true
                                addRowDragActive = true
                            }
                        }
                        if (up) {
                            // 松手时重新计时:3s 隐藏窗口从松手起算
                            // (否则长拖拽结束时窗口已耗尽,行一闪而过看起来像"拉不出")
                            if (dragged) addRowHideTick++
                            break
                        }
                    }
                    addRowDragActive = false
                }
            },
        state = scrollState,
        // 竖屏列表完全放得下时才禁用原生滚动(纵向拖动穿透到壁纸开抽屉);
        // 只要列表能朝任一方向滚动就保持原生滚动(否则滑到底后无法往回滑)
        userScrollEnabled = landscape || scrollState.canScrollForward || scrollState.canScrollBackward,
    ) {
        itemsIndexed(items, key = { _, it -> it.app.component }) { index, item ->
            val revealed = index == revealedIndex
            val currentRevealed by rememberUpdatedState(revealed)
            // 内容左移量(px):左滑拖拽跟手,松手按阈值落定;只有本行动,其余行原位
            val shift = remember { Animatable(0f) }
            val buttonsAlpha = (shift.value / actionWidthPx).coerceIn(0f, 1f)
            // 拖动排序时被拖行的上浮量(px);拖动中其他行保持原位,松手后才插位
            val lift = remember { Animatable(0f) }
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
                    .animateItem()
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (lift.value != 0f) 1f else 0f)
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
                            .requiredWidth(with(density) { (contentBounds.width + actionWidth().toPx() + 12.dp.toPx()).toDp() })
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
                // 内容层:整行承载手势与 Material 水波纹 —— 触发区 = 整行宽(与抽屉行一致);
                // 行内空白不再漏给壁纸层的手势(长按开设置/上滑开抽屉只在列表视口外生效)
                // 左滑时内容整体左移(只有本行)
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (landscape) 12.dp else PORTRAIT_ROW_MARGIN)
                        .graphicsLayer {
                            // 左滑钳制:内容最多滑到离屏幕左缘 12dp,不跑出屏幕
                            // (竖屏内容靠左,全量滑出会越界;横屏内容靠右,钳制不生效)
                            val maxSlide = (contentBounds.left - 12.dp.toPx()).coerceAtLeast(0f)
                            translationX = -minOf(buttonsAlpha * actionWidthPx, maxSlide)
                        }
                        .indication(interactionSource, LocalIndication.current)
                        .pointerInput(item.app.component, index) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val press = PressInteraction.Press(down.position)
                                scope.launch { interactionSource.emit(press) }
                                when (val outcome = awaitPressOutcome(down)) {
                                    PressOutcome.Tap -> {                                        scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                        if (currentRevealed) setRevealed(-1)
                                        else currentOnLaunch(item)
                                    }
                                    PressOutcome.LongPress -> {
                                        if (currentRevealed) {
                                            scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                            setRevealed(-1)
                                        } else {
                                            // 长按超时后 300ms 宽限:手指移动 → 拖动排序;
                                            // 不动 → 立即进选择页(不等松手,选择页不会误读当前手势)
                                            currentOnHoldChange(true)
                                            try {
                                                var reorder = false
                                                var finalTarget = index
                                                val slop = viewConfiguration.touchSlop
                                                // 宽限期内监听:移动超过阈值进入排序;松手/超时则进选择页
                                                val movedOrUp = withTimeoutOrNull(450) {
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                                        var up = false
                                                        for (change in event.changes) {
                                                            if (change.id != down.id) continue
                                                            if (change.changedToUp()) { up = true; break }
                                                            change.consume()
                                                            if (abs(change.position.y - down.position.y) > slop * 2f) {
                                                                reorder = true
                                                                return@withTimeoutOrNull true
                                                            }
                                                        }
                                                        if (up) break
                                                    }
                                                    false
                                                }
                                                if (reorder) {
                                                    // 拖动排序:被拖行跟手,松手按落位重排
                                                    scope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                                        var up = false
                                                        for (change in event.changes) {
                                                            if (change.id != down.id) continue
                                                            if (change.changedToUp()) { up = true; break }
                                                            change.consume()
                                                            // 注意:坐标已被本行 graphicsLayer(lift)反向平移,
                                                            // 必须加回 lift 才是真实跟手位移,否则行只跟手一半并抖动
                                                            val dy = change.position.y - down.position.y + lift.value
                                                            scope.launch { lift.snapTo(dy) }
                                                            finalTarget = (index + (dy / rowHeightPx).roundToInt())
                                                                .coerceIn(0, currentItems.size - 1)
                                                        }
                                                        if (up) break
                                                    }
                                                    scope.launch { lift.animateTo(0f) }
                                                    if (finalTarget != index) currentOnReorder(index, finalTarget)
                                                } else {
                                                    // 未拖动(宽限超时或松手):立即进选择页
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
                                icon = if (showOriginalColor) {
                                    rememberColorIcon(item.app, iconSize)
                                } else {
                                    rememberMonoIcon(item.app, iconSize)
                                },
                                size = iconSize,
                                badgeCount = if (showBadges) (badgeCounts[item.app.badgeKey] ?: 0) else 0,
                            )
                            Spacer(Modifier.width(if (landscape) 18.dp else 16.dp))
                        }
                        // 关闭图标显示时,角标叠在名称右上角(与抽屉/选择器同款)——桌面行是内联行,
                        // 不能只改共用 AppRow,这里单独加(修过的坑)
                        Box {
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
                            if (!showIcons && showBadges && (badgeCounts[item.app.badgeKey] ?: 0) > 0) {
                                BadgeDot(
                                    badgeCounts[item.app.badgeKey] ?: 0,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(
                                            x = 8.dp,
                                            y = badgeTopShiftDp(fontSize, item.displayName),
                                        ),
                                )
                            }
                        }
                    }
                }
                // 操作按钮层:必须画在内容层之上(后绘 = 命中优先)——内容层 fillMaxSize 即使透明也会挡按钮的点击(修过的坑);
                // 未展开时按钮不挂 clickable,命中直接穿透给内容层,行手势不受影响
                RevealButtonBar(
                    actions = listOf(
                        RowAction(
                            label = "改名",
                            onClick = {
                                setRevealed(-1)
                                currentOnRename(index)
                            },
                        ),
                        RowAction(
                            label = "移除",
                            destructive = true,
                            onClick = {
                                setRevealed(-1)
                                currentOnRemove(index)
                            },
                        ),
                    ),
                    alpha = buttonsAlpha,
                    slidePx = with(density) { 20.dp.toPx() },
                    rowHeight = rowHeight,
                    enabled = revealed,
                    buttonWidth = actionWidth() / 2,
                    onAction = { it.onClick(item.app) },
                )
            }
        }

        if (items.size < maxApps) {
            item(key = "__add__") {
                // 视觉显隐 + 高度动画:隐藏后不占视口空间,显示时淡入+展开,隐藏时淡出+收起
                AnimatedVisibility(
                    visible = addRowShown,
                    enter = fadeIn(tween(280)) + expandVertically(tween(280)),
                    exit = fadeOut(tween(280)) + shrinkVertically(tween(280)),
                ) {
                    AddRow(
                        iconSize = iconSize,
                        fontSize = fontSize,
                        showIcons = showIcons,
                        landscape = landscape,
                        onClick = onAdd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            // 横屏与普通行一致用 12dp;竖屏用主屏行边距 PORTRAIT_ROW_MARGIN
                            // (横屏曾误用 80dp 导致"添加应用"名称被裁掉)
                            .padding(horizontal = if (landscape) 12.dp else PORTRAIT_ROW_MARGIN),
                    )
                }
            }
        }
    }
}
