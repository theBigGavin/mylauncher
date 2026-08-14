package com.mylauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mylauncher.badges.BadgeStore
import com.mylauncher.data.AppEntry
import com.mylauncher.icons.warmUpIcons
import com.mylauncher.data.AppRepository
import com.mylauncher.data.DefaultApps
import com.mylauncher.data.HomeStore
import com.mylauncher.data.StoredEntry
import com.mylauncher.LauncherEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface PickerRequest {
    data class Replace(val index: Int) : PickerRequest
    data object Add : PickerRequest
}

/** 主屏总装配:方向感知布局 + 全部浮层(改名 / 选择器 / 设置 / 抽屉)。 */
@Composable
fun HomeScreen(innerDisplayUnfolded: Boolean = false) {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    val store = remember { HomeStore(context) }
    val scope = rememberCoroutineScope()

    val allApps by repo.apps.collectAsState()
    val homeData by store.data.collectAsState(initial = null)
    val badgeCounts by BadgeStore.counts.collectAsState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { repo.refresh() }
        KnockSound.init(context)
    }

    // 监听安装 / 卸载 / 更新,刷新应用列表
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                repo.refreshAsync()
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val apps = allApps
    val data = homeData

    // 首次启动:默认加载常用应用
    LaunchedEffect(apps, data?.initialized) {
        if (apps != null && data != null && !data.initialized && apps.isNotEmpty()) {
            store.setEntries(DefaultApps.pick(apps).map { StoredEntry(it.component, null) })
        }
    }

    // 当前形态(竖屏/横屏/内屏展开)对应的壁纸裁切变换
    val curForm = if (innerDisplayUnfolded) HomeStore.WALLPAPER_FORM_INNER
    else if (LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp)
        HomeStore.WALLPAPER_FORM_LANDSCAPE
    else HomeStore.WALLPAPER_FORM_PORTRAIT
    val wpScale = when (curForm) {
        HomeStore.WALLPAPER_FORM_INNER -> data?.customWallpaperScaleInner ?: 1f
        HomeStore.WALLPAPER_FORM_LANDSCAPE -> data?.customWallpaperScaleLandscape ?: 1f
        else -> data?.customWallpaperScale ?: 1f
    }
    val wpOffsetX = when (curForm) {
        HomeStore.WALLPAPER_FORM_INNER -> data?.customWallpaperOffsetXInner ?: 0f
        HomeStore.WALLPAPER_FORM_LANDSCAPE -> data?.customWallpaperOffsetXLandscape ?: 0f
        else -> data?.customWallpaperOffsetX ?: 0f
    }
    val wpOffsetY = when (curForm) {
        HomeStore.WALLPAPER_FORM_INNER -> data?.customWallpaperOffsetYInner ?: 0f
        HomeStore.WALLPAPER_FORM_LANDSCAPE -> data?.customWallpaperOffsetYLandscape ?: 0f
        else -> data?.customWallpaperOffsetY ?: 0f
    }

    // 后台预热全部应用图标(IO 线程):抽屉/选择器打开时直接命中缓存,
    // 避免首次打开时现场走 PackageManager 拉图标,拖慢主线程导致点击延迟
    val warmSizePx = with(LocalDensity.current) { ((data?.iconSizeDp ?: 40).dp).roundToPx() }.coerceAtLeast(24)
    LaunchedEffect(apps, warmSizePx, data?.showOriginalColor) {
        if (apps != null) {
            warmUpIcons(context, apps, warmSizePx, color = data?.showOriginalColor == true)
        }
    }

    // 解析持久化条目为真实 App;已卸载的条目静默剔除
    val items = remember(apps, data) {
        if (apps == null || data == null) {
            emptyList()
        } else {
            data.entries.mapNotNull { se ->
                apps.firstOrNull { it.component == se.component }
                    ?.let { HomeItem(it, se.customName) }
            }
        }
    }
    LaunchedEffect(items, apps, data) {
        if (apps != null && data != null && data.initialized && items.size != data.entries.size) {
            store.setEntries(items.map { StoredEntry(it.app.component, it.customName) })
        }
    }

    var picker by remember { mutableStateOf<PickerRequest?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    var renameIndex by remember { mutableIntStateOf(-1) }
    var showSettings by remember { mutableStateOf(false) }
    // 行内长按接管中(拖动排序):壁纸的"长按开设置""上滑开抽屉"在此期间不触发
    var rowHolding by remember { mutableStateOf(false) }
    // 功德彩蛋:活跃气泡列表 + 触发序号(系统返回手势触发)
    var meritBubbles by remember { mutableStateOf(listOf<MeritBubbleData>()) }
    var meritSeq by remember { mutableIntStateOf(0) }
    val currentData by rememberUpdatedState(data)
    // 返回手势触发功德彩蛋:声音 + 功德+1 + 冒泡
    LaunchedEffect(Unit) {
        LauncherEvents.backGesture.collect {
            // 浮层打开时不触发(浮层的返回会先被 Compose BackHandler 消费)
            if (picker == null && !drawerOpen && !showSettings) {
                scope.launch { store.addMerit() }
                val d = currentData
                if (d?.easterEggEnabled == true) {
                    meritSeq++
                    meritBubbles = meritBubbles + MeritBubbleData(meritSeq, (d.meritCount) + 1)
                    if (d.meritSoundEnabled == true) {
                        KnockSound.play()
                    }
                }
            }
        }
    }

    // 更换壁纸:交给系统壁纸管理器(选择 + 裁切 + 横竖屏/内屏适配全由系统处理),
    // 桌面用 FLAG_SHOW_WALLPAPER 跟随系统壁纸
    fun openSystemWallpaper() {
        scope.launch { store.setWallpaperMode(HomeStore.WALLPAPER_SYSTEM) }
        runCatching { context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }
    }

    // 背景:内置几何壁纸(默认)/ 跟随系统壁纸(FLAG_SHOW_WALLPAPER + 暗纱)/
    // 自定义图片(自绘 bitmap,盖 20% 暗纱;文件丢失回退内置)
    val systemMode = data?.wallpaperMode == HomeStore.WALLPAPER_SYSTEM
    val customMode = data?.wallpaperMode == HomeStore.WALLPAPER_CUSTOM
    ApplyShowWallpaperFlag(enabled = systemMode)
    val customWallpaper = rememberCustomWallpaper(enabled = customMode)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 竖横屏按宽高比;折叠屏内屏展开(接近方形、宽<高)也强制横屏布局
        val landscape = maxWidth > maxHeight || innerDisplayUnfolded
        val topSpace = maxHeight * 0.20f
        val listSpace = maxHeight * 0.05f
        val bottomSpace = maxHeight * 0.10f // 底部空白触发区(上滑开抽屉)
        // 列表视口高度:横竖屏分开配置
        val heightPercent = if (landscape) data?.listHeightPercentLandscape ?: 100
        else data?.listHeightPercent ?: 50
        val listHeight = maxHeight * heightPercent / 100f
        // 手势挂在背景层上:长按空白开设置(行内长按由行自己处理,行更深先收到事件)、上滑开抽屉
        val bgModifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        if (!rowHolding && picker == null && !drawerOpen && renameIndex < 0 && !showSettings) {
                            showSettings = true
                        }
                    },
                )
            }
            // 边缘滑入:手势一开始移动就敲木鱼(跟手),不等待返回手势完成
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val edge = 44.dp.toPx()
                    val fromLeft = down.position.x < edge
                    val fromRight = down.position.x > size.width - edge
                    if (!fromLeft && !fromRight) return@awaitEachGesture
                    var dx = 0f
                    var knocked = false
                    drag(down.id) { change ->
                        if (!knocked) {
                            dx += change.positionChange().x
                            val inward = if (fromLeft) dx > 0 else dx < 0
                            if (inward && abs(dx) > 18.dp.toPx()) {
                                knocked = true
                                knockNow()
                            }
                        }
                        change.consume()
                    }
                }
            }
            .pointerInput(Unit) {
                var upward = false
                var startY = 0f
                // 只在屏幕底部 30% 区域上滑才开抽屉:顶部/中部是时钟与列表,避免误触
                val triggerTop = size.height * 0.70f
                detectVerticalDragGestures(
                    onDragStart = { startY = it.y },
                    onDragEnd = {
                        if (upward && startY > triggerTop &&
                            !rowHolding && picker == null && renameIndex < 0 && !showSettings && !drawerOpen
                        ) {
                            drawerOpen = true
                        }
                        upward = false
                    },
                    onDragCancel = { upward = false },
                ) { _, dragAmount ->
                    if (dragAmount < 0) upward = true
                }
            }
        if (systemMode) {
            SystemWallpaperScrim(bgModifier)
        } else if (customMode && customWallpaper != null) {
            Box(bgModifier) {
                Image(
                    bitmap = customWallpaper,
                    contentDescription = null,
                    // 铺满基准缩放 × 用户裁切缩放 + 平移(与裁切屏同一套变换)
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val cover = maxOf(
                                size.width / customWallpaper.width,
                                size.height / customWallpaper.height,
                            )
                            val total = cover * wpScale.coerceIn(1f, 5f)
                            scaleX = total
                            scaleY = total
                            translationX = wpOffsetX * size.width
                            translationY = wpOffsetY * size.height
                        },
                    contentScale = ContentScale.Fit,
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            }
        } else {
            Wallpaper(bgModifier)
        }

        // 二级页面(选择器/抽屉/设置页)打开时隐藏主屏内容:
        // 页面背景透明才能透出窗口下方(被模糊的)系统壁纸;
        // 主屏 UI 与页面在同一个窗口里,不隐藏的话会透到页面上
        val pageOpen = picker != null || drawerOpen || showSettings
        val homeAlpha = if (pageOpen) 0f else 1f

        if (data != null) {
            val iconSize = data.iconSizeDp.dp
            val fontSize = data.fontSizeSp.sp
            val rowSpacing = data.rowSpacingDp.dp

            if (!landscape) {
                // 竖屏:居中超大时钟 + 竖行列表 + 底部计数
                Column(Modifier.fillMaxSize().alpha(homeAlpha)) {
                    Spacer(Modifier.height(topSpace))
                    ClockWidget(
                        landscape = false,
                        modifier = Modifier.fillMaxWidth(),
                        meritBubbles = meritBubbles,
                        onMeritBubbleDone = { id ->
                            meritBubbles = meritBubbles.filterNot { it.id == id }
                        },
                    )
                    Spacer(Modifier.height(listSpace))
                    AppList(
                        items = items,
                        iconSize = iconSize,
                        fontSize = fontSize,
                        rowSpacing = rowSpacing,
                        showIcons = data.showIcons,
                        showOriginalColor = data.showOriginalColor,
                        showBadges = data.showBadges,
                        badgeCounts = badgeCounts,
                        landscape = false,
                        onLaunch = { launch(context, repo, it.app) },
                        onReplace = { picker = PickerRequest.Replace(it) },
                        onRename = { renameIndex = it },
                        onRemove = { i ->
                            scope.launch {
                                val list = data.entries.toMutableList()
                                if (i < list.size) {
                                    list.removeAt(i)
                                    store.setEntries(list)
                                }
                            }
                        },
                        onAdd = { picker = PickerRequest.Add },
                        onHoldChange = { rowHolding = it },
                        onReorder = { from, to ->
                            scope.launch {
                                val list = data.entries.toMutableList()
                                if (from in list.indices && to in list.indices) {
                                    val e = list.removeAt(from)
                                    list.add(to, e)
                                    store.setEntries(list)
                                }
                            }
                        },
                        modifier = Modifier
                            .height(listHeight)
                            .fillMaxWidth(),
                    )
                    // 底部空白区:列表下方保留 10% 屏高,此处上滑可拉出抽屉
                    // (列表自身的纵向拖动被滚动/行手势消费,必须留出列表之外的触发区;
                    // 计数条已移除 —— 它遮挡底部上滑手势)
                    Spacer(Modifier.height(bottomSpace))
                }
            } else {
                // 横屏:时钟左下,列表靠右单列(图标一列垂线、名称右对齐)
                // 边距/字号全部按屏幕尺寸自适应(折叠内屏等方形屏同样适用):
                //   hMargin = 屏宽 5.5%,clamp [32, 120]dp —— 列表右缘与时钟左缘共用
                //   vMargin = 屏高 6%(顶)/ 5%(底),clamp —— 列表上下留白
                //   listWidth = 内容(icon+名称列+间距) + ACTION_WIDTH(左滑露出区) + 边距
                //   时钟可用宽 = 屏宽 - hMargin - listWidth - 16dp 间距,字号受它约束,不与列表重叠
                val hMargin = (maxWidth * 0.055f).coerceIn(32.dp, 120.dp)
                val vMarginTop = (maxHeight * 0.06f).coerceIn(24.dp, 72.dp)
                val vMarginBottom = (maxHeight * 0.05f).coerceIn(20.dp, 80.dp)
                val listWidth = with(LocalDensity.current) {
                    (iconSize.toPx() + (fontSize * 5.5f).toPx() + 18.dp.toPx() +
                        20.dp.toPx() + hMargin.toPx() + 12.dp.toPx() + 12.dp.toPx() +
                        actionWidth().toPx()).toDp()
                }
                val clockAvailWidth =
                    (maxWidth - hMargin - listWidth - 16.dp).coerceAtLeast(120.dp)
                Box(Modifier.fillMaxSize().alpha(homeAlpha)) {
                    ClockWidget(
                        landscape = true,
                        availableWidthDp = clockAvailWidth.value,
                        meritBubbles = meritBubbles,
                        onMeritBubbleDone = { id ->
                            meritBubbles = meritBubbles.filterNot { it.id == id }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = hMargin, bottom = vMarginBottom),
                    )
                    AppList(
                        items = items,
                        iconSize = iconSize,
                        fontSize = fontSize,
                        rowSpacing = rowSpacing,
                        showIcons = data.showIcons,
                        showOriginalColor = data.showOriginalColor,
                        showBadges = data.showBadges,
                        badgeCounts = badgeCounts,
                        landscape = true,
                        onLaunch = { launch(context, repo, it.app) },
                        onReplace = { picker = PickerRequest.Replace(it) },
                        onRename = { renameIndex = it },
                        onRemove = { i ->
                            scope.launch {
                                val list = data.entries.toMutableList()
                                if (i < list.size) {
                                    list.removeAt(i)
                                    store.setEntries(list)
                                }
                            }
                        },
                        onAdd = { picker = PickerRequest.Add },
                        onHoldChange = { rowHolding = it },
                        onReorder = { from, to ->
                            scope.launch {
                                val list = data.entries.toMutableList()
                                if (from in list.indices && to in list.indices) {
                                    val e = list.removeAt(from)
                                    list.add(to, e)
                                    store.setEntries(list)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .height(listHeight)
                            .width(listWidth)
                            .padding(start = 20.dp, end = hMargin, top = vMarginTop, bottom = vMarginBottom)
                    )
                }
            }

            // ---- 浮层 ----
            if (renameIndex >= 0 && renameIndex < items.size) {
                RenameDialog(
                    initial = items[renameIndex].displayName,
                    onConfirm = { name ->
                        val i = renameIndex
                        renameIndex = -1
                        scope.launch {
                            val list = data.entries.toMutableList()
                            if (i < list.size) {
                                list[i] = list[i].copy(customName = name)
                                store.setEntries(list)
                            }
                        }
                    },
                    onDismiss = { renameIndex = -1 },
                )
            }

            if (showSettings) {
                SettingsScreen(
                    iconSize = data.iconSizeDp,
                    fontSize = data.fontSizeSp,
                    rowSpacing = data.rowSpacingDp,
                    showIcons = data.showIcons,
                    showOriginalColor = data.showOriginalColor,
                    showBadges = data.showBadges,
                    wallpaperMode = data.wallpaperMode,
                    customScale = wpScale,
                    customOffsetX = wpOffsetX,
                    customOffsetY = wpOffsetY,
                    listHeightPercent = data.listHeightPercent,
                    listHeightPercentLandscape = data.listHeightPercentLandscape,
                    easterEggEnabled = data.easterEggEnabled,
                    meritSoundEnabled = data.meritSoundEnabled,
                    onIconSize = { scope.launch { store.setIconSize(it) } },
                    onFontSize = { scope.launch { store.setFontSize(it) } },
                    onRowSpacing = { scope.launch { store.setRowSpacing(it) } },
                    onShowIcons = { scope.launch { store.setShowIcons(it) } },
                    onShowBadges = { scope.launch { store.setShowBadges(it) } },
                    onShowOriginalColor = { scope.launch { store.setShowOriginalColor(it) } },
                    onEasterEgg = { scope.launch { store.setEasterEggEnabled(it) } },
                    onMeritSound = { scope.launch { store.setMeritSoundEnabled(it) } },
                    onPickSystemWallpaper = {
                        openSystemWallpaper()
                    },
                    onListHeight = { form, v -> scope.launch { store.setListHeightPercent(form, v) } },
                    onReset = {
                        showSettings = false
                        scope.launch {
                            store.resetAll(
                                DefaultApps.pick(apps ?: emptyList())
                                    .map { StoredEntry(it.component, null) }
                            )
                        }
                    },
                    onDismiss = { showSettings = false },
                )
            }

        }
    }

    // 全屏选择器(独立于主布局,覆盖全屏)
    val req = picker
    if (req != null && apps != null && data != null) {
        PickerScreen(
            apps = apps,
            iconSize = data.iconSizeDp.dp,
            fontSize = data.fontSizeSp.sp,
            showIcons = data.showIcons,
            showOriginalColor = data.showOriginalColor,
            wallpaperMode = data.wallpaperMode,
            adding = req is PickerRequest.Add,
            onPick = { entry ->
                when (req) {
                    is PickerRequest.Add -> scope.launch {
                        val cur = data.entries
                        if (cur.none { it.component == entry.component } &&
                            cur.size < HomeStore.MAX_APPS
                        ) {
                            store.setEntries(cur + StoredEntry(entry.component, null))
                        }
                    }
                    is PickerRequest.Replace -> scope.launch {
                        val i = req.index
                        val cur = data.entries.toMutableList()
                        if (i < cur.size) {
                            val dup = cur.indexOfFirst { it.component == entry.component }
                            if (dup >= 0 && dup != i) cur.removeAt(dup)
                            val idx = if (dup >= 0 && dup < i) i - 1 else i
                            if (idx < cur.size) {
                                cur[idx] = StoredEntry(entry.component, null)
                            }
                            store.setEntries(cur)
                        }
                    }
                }
                picker = null
            },
            onDismiss = { picker = null },
        )
    }

    // 应用抽屉(空白处上滑拉出)
    AnimatedVisibility(
        visible = drawerOpen && apps != null && data != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        if (apps != null && data != null) {
            AppDrawer(
                apps = apps,
                iconSize = data.iconSizeDp.dp,
                fontSize = data.fontSizeSp.sp,
                showIcons = data.showIcons,
                showOriginalColor = data.showOriginalColor,
                wallpaperMode = data.wallpaperMode,
                customScale = wpScale,
                customOffsetX = wpOffsetX,
                customOffsetY = wpOffsetY,
                onAddToHome = { entry ->
                    scope.launch {
                        val cur = data.entries
                        if (cur.none { it.component == entry.component } &&
                            cur.size < HomeStore.MAX_APPS
                        ) {
                            store.setEntries(cur + StoredEntry(entry.component, null))
                        }
                    }
                },
                onDismiss = { drawerOpen = false },
            )
        }
    }

}

private fun launch(context: Context, repo: AppRepository, entry: AppEntry) {
    runCatching { context.startActivity(repo.launchIntent(entry)) }
}
