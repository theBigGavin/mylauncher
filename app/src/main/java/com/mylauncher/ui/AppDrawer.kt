package com.mylauncher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylauncher.data.AppEntry
import com.mylauncher.icons.rememberColorIcon
import com.mylauncher.icons.rememberMonoIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 应用抽屉:空白处上滑拉出;点击启动;行内左滑露出 放入桌面/删除/信息;点行首星标收藏(收藏置顶)。 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    iconSize: Dp,
    fontSize: TextUnit,
    showIcons: Boolean,
    showOriginalColor: Boolean,
    wallpaperMode: String,
    customScale: Float,
    customOffsetX: Float,
    customOffsetY: Float,
    favorites: Set<String>,
    showSystem: Boolean,
    onShowSystemChange: (Boolean) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAddToHome: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AppListOverlay(
        title = "所有应用",
        subtitle = "点击启动 · 左滑管理 · ★收藏置顶",
        apps = apps,
        iconSize = iconSize,
        fontSize = fontSize,
        showIcons = showIcons,
        showOriginalColor = showOriginalColor,
        wallpaperMode = wallpaperMode,
        customScale = customScale,
        customOffsetX = customOffsetX,
        customOffsetY = customOffsetY,
        favorites = favorites,
        onToggleFavorite = onToggleFavorite,
        showSystem = showSystem,
        onShowSystemChange = onShowSystemChange,
        rowActions = { entry ->
            // 应用分身只提供"放入桌面":删除/信息走的是主用户路径,对分身不适用
            if (entry.isMainUser) {
                listOf(
                    RowAction(label = "放入", onClick = { onAddToHome(it) }),
                    RowAction(label = "删除", destructive = true, onClick = { deleteApp(context, it) }),
                    RowAction(label = "信息", onClick = { appInfo(context, it) }),
                )
            } else {
                listOf(
                    RowAction(label = "放入", onClick = { onAddToHome(it) }),
                )
            }
        },
        // 点击行 = 启动该应用(与副标题"点击启动"一致)
        onRowClick = { entry ->
            onLaunch(entry)
            // 启动后收起抽屉:从应用返回时直接回主屏,不再停留在抽屉
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/** 收藏星标:实心 = 已收藏,空心描边 = 未收藏。Canvas 自绘五角星,保证单色 Zune 风格。 */
@Composable
private fun FavoriteStar(filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val outer = size.minDimension / 2f
        val inner = outer * 0.382f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val ang = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + r * kotlin.math.cos(ang).toFloat()
            val y = cy + r * kotlin.math.sin(ang).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val color = Color.White.copy(alpha = if (filled) 0.9f else 0.30f)
        if (filled) {
            drawPath(path, color)
        } else {
            drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}

private fun deleteApp(context: Context, entry: AppEntry) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.packageName}")))
    }
}

private fun appInfo(context: Context, entry: AppEntry) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", entry.packageName, null))
        )
    }
}

/**
 * 全屏应用列表层(抽屉 / 选择器共用):
 * 标题 + 系统应用开关 + 可滚动列表,点空白或返回键关闭。
 * 关闭用的点按检测必须用 detectTapGestures —— clickable 会吞掉滚动事件,导致列表无法滚动。
 * 行内左滑管理与桌面左滑同款手势(awaitPressOutcome 自定义检测,行内标准检测器不可靠)。
 */
@Composable
internal fun AppListOverlay(
    title: String,
    subtitle: String,
    apps: List<AppEntry>,
    iconSize: Dp,
    fontSize: TextUnit,
    showIcons: Boolean,
    showOriginalColor: Boolean = false,
    wallpaperMode: String,
    customScale: Float = 1f,
    customOffsetX: Float = 0f,
    customOffsetY: Float = 0f,
    onDismiss: () -> Unit,
    onRowClick: ((AppEntry) -> Unit)? = null,
    rowActions: ((AppEntry) -> List<RowAction>)? = null,
    favorites: Set<String> = emptySet(),
    onToggleFavorite: ((AppEntry) -> Unit)? = null,
    showSystem: Boolean = false,
    onShowSystemChange: (Boolean) -> Unit = {},
) {
    BackHandler(onBack = onDismiss)
    // 系统应用显示开关:初始值来自持久化设置,用户切换后回写(记住用户习惯)
    var query by remember { mutableStateOf("") }
    val visible = remember(apps, showSystem, query, favorites) {
        val base = if (showSystem) apps else apps.filter { !it.isSystem }
        val filtered = if (query.isBlank()) {
            base
        } else {
            base.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        // 收藏置顶:收藏在前,组内保持仓库层排序(stable sort 不改变原顺序)
        filtered.sortedByDescending { it.component in favorites }
    }
    val config = LocalConfiguration.current
    val currentOnRowClick by rememberUpdatedState(onRowClick)
    val currentRowActions by rememberUpdatedState(rowActions)
    val density = androidx.compose.ui.platform.LocalDensity.current
    // 三个按钮的总宽:窄屏(手机竖屏)缩短,避免盖住左侧内容;与桌面左滑按钮同一套自适应
    val actionWidthPx = with(density) { actionWidth().toPx() }

    Box(Modifier.fillMaxSize()) {
        GlassPageBackground(
            wallpaperMode,
            Modifier.fillMaxSize(),
            customScale = customScale,
            customOffsetX = customOffsetX,
            customOffsetY = customOffsetY,
        )
        // 空白处点按关闭(不吞列表滚动事件);空白处下滑同样收起
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
                .pointerInput(Unit) {
                    var downward = false
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (downward) onDismiss()
                            downward = false
                        },
                        onDragCancel = { downward = false },
                    ) { _, dragAmount ->
                        if (dragAmount > 0) downward = true
                    }
                }
        )
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height((config.screenHeightDp * 0.10f).dp))
            BasicText(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "$subtitle · 共 ${visible.size} 个应用",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "显示系统应用",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                MiniSwitch(checked = showSystem, onChange = { onShowSystemChange(it) })
            }
            Spacer(Modifier.height(16.dp))

            // 搜索框:圆角底框,按名称/包名过滤
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LIST_H_MARGIN, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                if (query.isEmpty()) {
                    BasicText(
                        text = "搜索应用",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.sp,
                        ),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp,
                    ),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))

            val rowHeight = appRowHeight(iconSize, fontSize)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(visible, key = { it.component }) { app ->
                    val actions = currentRowActions?.invoke(app)
                    if (actions == null || actions.isEmpty()) {
                        // 简单行:点击启动(选择器模式)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .clickable { currentOnRowClick?.invoke(app) }
                        ) {
                            AppRow(
                                name = app.label,
                                icon = if (showOriginalColor) {
                                    rememberColorIcon(app, iconSize)
                                } else {
                                    rememberMonoIcon(app, iconSize)
                                },
                                iconSize = iconSize,
                                fontSize = fontSize,
                                showIcons = showIcons,
                                landscape = false,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        horizontal = if (config.screenWidthDp > config.screenHeightDp)
                                            LIST_H_MARGIN else PORTRAIT_ROW_MARGIN
                                    ),
                            )
                        }
                    } else {
                        // 管理行:整行可手势,左滑露出右侧三个文字操作。
                        // 内容不位移(抽屉行内容靠左,左移会被裁出屏幕);按钮跟手 + 淡入 + 行背景反馈。
                        var revealed by remember { mutableStateOf(false) }
                        var dragProgress by remember { mutableStateOf(0f) } // 拖拽进度 0..1,驱动按钮跟手
                        // 展开后无操作超时自动收起(点按钮/再滑右都会重置计时)
                        LaunchedEffect(revealed) {
                            if (revealed) {
                                delay(3000)
                                revealed = false
                            }
                        }
                        val buttonsAlpha by animateFloatAsState(
                            targetValue = if (revealed) 1f else dragProgress,
                            label = "drawerRevealAlpha",
                        )
                        val rowBgAlpha by animateFloatAsState(
                            targetValue = if (revealed) 0.12f
                            else if (dragProgress > 0f) 0.08f
                            else 0f,
                            label = "drawerRevealBg",
                        )
                        val currentActions by rememberUpdatedState(actions)
                        val currentApp by rememberUpdatedState(app)
                        val currentRevealed by rememberUpdatedState(revealed)
                        val currentFavorites by rememberUpdatedState(favorites)
                        val currentToggleFavorite by rememberUpdatedState(onToggleFavorite)
                        val slidePx = with(density) { 24.dp.toPx() }
                        // Material 水波纹:手势行手动发射按压交互(触点 = 手指位置)
                        val interactionSource = remember { MutableInteractionSource() }
                        val scope = rememberCoroutineScope()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .clipToBounds()
                                .indication(interactionSource, LocalIndication.current)
                                .pointerInput(app.component) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        // 行内子按钮(收藏星标/右侧操作)已消费的按下:静候抬手即结束本手势,
                                        // 不处理也不消费 —— 否则会抢按钮的点击(导致按钮点不中/点击被取消)
                                        if (down.isConsumed) {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                if (event.changes.any { it.changedToUpIgnoreConsumed() }) break
                                            }
                                            return@awaitEachGesture
                                        }
                                        val press = PressInteraction.Press(down.position)
                                        scope.launch { interactionSource.emit(press) }
                                        when (val outcome = awaitPressOutcome(down)) {
                                            PressOutcome.Tap -> {
                                                scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                                if (currentRevealed) revealed = false
                                                else currentOnRowClick?.invoke(app)
                                            }
                                            PressOutcome.SwipeLeft -> {
                                                if (!currentRevealed) {
                                                    var dx = 0f
                                                    drag(down.id) { change ->
                                                        dx += change.positionChange().x
                                                        dragProgress = (-dx / actionWidthPx).coerceIn(0f, 1f)
                                                        change.consume()
                                                    }
                                                    scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                                    dragProgress = 0f
                                                    if (dx < -actionWidthPx * 0.3f) revealed = true
                                                }
                                            }
                                            PressOutcome.SwipeRight -> {
                                                scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                                revealed = false
                                            }
                                            PressOutcome.LongPress -> {
                                                // 部分设备/注入下 UP 延迟超过长按时限,点击会被判成长按 ——
                                                // 未展开时同样启动应用,保证点击可用。
                                                // 但慢速滚动时手指会在长按窗口内持续产生 sub-slop 位移:
                                                // 静候抬手,仅当手指始终静止(抬手时位移仍很小)才启动,
                                                // 否则按滚动意图放弃 —— 否则慢速滚动会误启动应用
                                                scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                                if (currentRevealed) {
                                                    revealed = false
                                                } else {
                                                    var moved = 0f
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                                        var up = false
                                                        for (change in event.changes) {
                                                            if (change.id != down.id) continue
                                                            moved = maxOf(
                                                                moved,
                                                                abs(change.position.x - down.position.x),
                                                                abs(change.position.y - down.position.y),
                                                            )
                                                            if (change.changedToUp()) {
                                                                up = true
                                                                break
                                                            }
                                                        }
                                                        if (up) break
                                                    }
                                                    if (moved < viewConfiguration.touchSlop * 0.4f) {
                                                        currentOnRowClick?.invoke(app)
                                                    }
                                                }
                                            }
                                            PressOutcome.Cancelled -> {
                                                scope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                                            }
                                        }
                                    }
                                }
                        ) {
                            // 选中行背景覆盖:让用户看出当前行处于"管理"状态
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = rowBgAlpha))
                            )
                            // 内容层(图标+名称,保持原位,全程可见;fillMaxSize 保证垂直居中与按钮对齐)
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(
                                        horizontal = if (config.screenWidthDp > config.screenHeightDp)
                                            LIST_H_MARGIN else PORTRAIT_ROW_MARGIN
                                    ),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                AppRow(
                                    name = app.label,
                                    icon = if (showOriginalColor) {
                                        rememberColorIcon(app, iconSize)
                                    } else {
                                        rememberMonoIcon(app, iconSize)
                                    },
                                    iconSize = iconSize,
                                    fontSize = fontSize,
                                    showIcons = showIcons,
                                    landscape = config.screenWidthDp > config.screenHeightDp,
                                )
                            }
                            // 右侧操作按钮:必须画在内容层之上(后绘 = 命中优先),
                            // 否则内容层 fillMaxSize 挡住按钮,点按永远落不到按钮上(修过的坑);
                            // 贴齐屏幕右缘,仅留少量右留白;不铺深色衬底:
                            // background 与 graphicsLayer 同链时 alpha 不生效(实测衬底常驻),
                            // 文字可读性靠 textShadow 保证
                            RevealButtonBar(
                                actions = currentActions,
                                alpha = buttonsAlpha,
                                slidePx = slidePx,
                                rowHeight = rowHeight,
                                enabled = revealed,
                                buttonWidth = actionWidth() / 3,
                                modifier = Modifier.padding(end = 12.dp),
                                onAction = { action ->
                                    revealed = false
                                    action.onClick(currentApp)
                                },
                            )
                            // 收藏星标:行首固定、全程可见(展开时也不淡出)。
                            // 抽屉行:有切换回调 → 显示星标(空/实)且可点按切换;
                            // 选择器行:无切换回调 → 仅收藏的应用显示实心星标,非收藏不显示空星标
                            val isFavorite = app.component in currentFavorites
                            if (currentToggleFavorite != null || isFavorite) {
                                Box(
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 16.dp)
                                        .size(34.dp)
                                        .then(
                                            if (currentToggleFavorite != null) {
                                                Modifier.clickable {
                                                    currentToggleFavorite?.invoke(currentApp)
                                                }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FavoriteStar(
                                        filled = isFavorite,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
