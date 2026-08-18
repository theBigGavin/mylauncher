package com.mylauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mylauncher.badges.BadgeService
import com.mylauncher.badges.BadgeStore
import com.mylauncher.data.AppEntry
import com.mylauncher.icons.warmUpIcons
import com.mylauncher.data.AppRepository
import com.mylauncher.data.DefaultApps
import com.mylauncher.data.HomeStore
import com.mylauncher.data.StoredEntry
import com.mylauncher.LauncherEvents
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    // 通知角标兜底:部分 ROM 会丢通知回调(角标停在旧值/清零),ON_RESUME 与可见期间周期重算
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) BadgeService.requestRefresh?.invoke()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            BadgeService.requestRefresh?.invoke()
            delay(5000)
        }
    }

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

    // 每次启动先跑存量数据迁移(幂等,见 applyLegacyMigrations);首次启动加载默认应用
    LaunchedEffect(apps, data?.initialized) {
        if (apps != null && data != null) {
            store.applyLegacyMigrations()
            if (!data.initialized && apps.isNotEmpty()) {
                store.setEntries(DefaultApps.pick(apps).map { StoredEntry(it.component, null) })
            }
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
        if (apps != null && data != null && data.initialized) {
            // 已卸载剔除 + 槽位数下调时的超额裁剪(槽位数上调不自动补)
            val overLimit = data.entries.size > data.maxApps
            if (items.size != data.entries.size || overLimit) {
                store.setEntries(
                    items.take(data.maxApps).map { StoredEntry(it.app.component, it.customName) }
                )
            }
        }
    }

    var picker by remember { mutableStateOf<PickerRequest?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    var renameIndex by remember { mutableIntStateOf(-1) }
    var showSettings by remember { mutableStateOf(false) }
    // 行内长按接管中(拖动排序):壁纸的"长按开设置""上滑开抽屉"在此期间不触发
    var rowHolding by remember { mutableStateOf(false) }
    // 功德彩蛋:活跃气泡列表 + 触发序号(边缘敲击即时触发,返回路径补触发)
    var meritBubbles by remember { mutableStateOf(listOf<MeritBubbleData>()) }
    var meritSeq by remember { mutableIntStateOf(0) }
    val currentData by rememberUpdatedState(data)
    // 边缘敲击去重状态:未消费的边缘敲击(标记 + 时刻)。返回完成回调到达时,
    // 若存在窗口内的未消费边缘敲击,只消费不补(同一手势两条路径去重);
    // 旧实现用全局 1s 时间窗去重:高速连续滑动时手指常落在 44dp 边缘区外,
    // 该手势没敲过,补敲却被上一个手势的边缘敲击误吞 —— 快速连滑会漏音(修过的坑)
    var edgeKnockPending by remember { mutableStateOf(false) }
    var edgeKnockMs by remember { mutableLongStateOf(0L) }
    // 边缘敲击与返回回调视为同一手势的最大间隔:超过则视作独立手势
    val edgeKnockWindowMs = 1500L
    // 最近一次返回路径补敲的时刻:系统对同一手势双调回调时,两次 emit 间隔 <100ms,
    // 只响第一声(真实快速连按 >100ms 的独立手势不受影响)
    var lastBackKnockMs by remember { mutableLongStateOf(0L) }
    // 功德 +1 + 冒泡(敲击/补敲/自动积累共用);冒泡文字用自定义功德文字
    fun grantMerit() {
        val d = currentData
        scope.launch { store.addMerit() }
        meritSeq++
        meritBubbles = meritBubbles +
            MeritBubbleData(meritSeq, (d?.meritCount ?: 0) + 1, d?.meritLabel ?: "功德")
    }
    // 边缘敲击(含纯点击):放音 + 功德+1 + 冒泡 即时触发。
    // 点击边缘不产生系统返回手势,功德/冒泡若只挂在返回回调上,点击就只有声音没有气泡(修过的坑)
    fun knockNow() {
        if (picker != null || drawerOpen || showSettings) return
        val d = currentData
        if (d?.easterEggEnabled != true) return
        edgeKnockMs = System.currentTimeMillis()
        edgeKnockPending = true
        if (d.meritSoundEnabled == true) {
            if (KnockSound.DEBUG_KNOCK) Log.d("MyLauncher", "knockNow[edge]: t=$edgeKnockMs")
            KnockSound.play()
        }
        grantMerit()
    }
    // 自动积累功德:开启后每秒 放音+功德+1+冒泡,连续积累时长上限 autoMeritMaxS 秒;
    // 时长用尽自动关闭自动积累开关(设置页开关同步回弹)
    val autoMeritOn = data?.autoMeritEnabled == true && data?.easterEggEnabled == true
    val autoMeritMaxS = data?.autoMeritMaxS ?: 10
    LaunchedEffect(autoMeritOn, autoMeritMaxS) {
        if (autoMeritOn) {
            repeat(autoMeritMaxS) {
                delay(1000)
                if (currentData?.meritSoundEnabled == true) KnockSound.play()
                grantMerit()
            }
            scope.launch { store.setAutoMeritEnabled(false) }
        }
    }

    // 返回手势触发功德彩蛋(补偿路径):无边缘敲击的手势(按键返回/手指落在边缘区外)
    // 在此补 功德+1 + 冒泡 + 声音;本手势边缘已敲过则只消费标记不补
    LaunchedEffect(Unit) {
        LauncherEvents.backGesture.collect {
            // 浮层打开时不触发(浮层的返回会先被 Compose BackHandler 消费)
            if (picker == null && !drawerOpen && !showSettings) {
                val d = currentData
                if (d?.easterEggEnabled == true) {
                    val now = System.currentTimeMillis()
                    // 同一手势去重:本手势边缘已敲(功德/冒泡/声音已在边缘路径给出)→ 只消费标记;
                    // 边缘没敲 → 补敲,与上一个手势的敲击无关 —— 快速连滑每个手势都响
                    // 返回路径自去重:系统对同一手势双调回调时两次 emit 间隔 <100ms,只响第一声
                    if (edgeKnockPending && now - edgeKnockMs <= edgeKnockWindowMs) {
                        edgeKnockPending = false
                        if (KnockSound.DEBUG_KNOCK) Log.d("MyLauncher", "backGesture[consume-edge]: t=$now edge=$edgeKnockMs")
                    } else {
                        edgeKnockPending = false
                        grantMerit()
                        if (d.meritSoundEnabled == true && now - lastBackKnockMs > 100) {
                            lastBackKnockMs = now
                            if (KnockSound.DEBUG_KNOCK) {
                                Log.d("MyLauncher", "backGesture[play]: t=$now lastBack=$lastBackKnockMs")
                            }
                            KnockSound.play()
                        } else if (KnockSound.DEBUG_KNOCK) {
                            Log.d("MyLauncher", "backGesture[skip-dedup]: t=$now lastBack=$lastBackKnockMs")
                        }
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

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // 边缘滑入:手指按下屏幕左右边缘的瞬间即敲木鱼(最跟手,不做移动判定)。
            // 挂在根容器上:根是所有子层(时钟/列表行/空白)的祖先,任何位置的边缘按下
            // 都能收到 —— 挂在背景层上会被列表行/滚动区遮挡,边缘敲击时有时无(修过的坑)
            // 手势级防重入:awaitEachGesture 每轮等待全部指针抬起后才进入下一轮,
            // 一次按下(含多点触控)只执行一轮 → 每次手势最多敲一次;
            // 兜底:个别设备双上报 down 时由 KnockSound 内部 80ms 物理防重入吸收
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val edge = 44.dp.toPx()
                    if (down.position.x < edge || down.position.x > size.width - edge) {
                        knockNow()
                    }
                }
            },
    ) {
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
                        maxApps = data.maxApps,
                        landscape = false,
                        onLaunch = { launch(repo, it.app) },
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
                        maxApps = data.maxApps,
                        landscape = true,
                        onLaunch = { launch(repo, it.app) },
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
                    maxApps = data.maxApps,
                    easterEggEnabled = data.easterEggEnabled,
                    easterEggUnlocked = data.easterEggUnlocked,
                    meritSoundEnabled = data.meritSoundEnabled,
                    meritLabel = data.meritLabel,
                    autoMeritEnabled = data.autoMeritEnabled,
                    autoMeritMaxS = data.autoMeritMaxS,
                    onIconSize = { scope.launch { store.setIconSize(it) } },
                    onFontSize = { scope.launch { store.setFontSize(it) } },
                    onRowSpacing = { scope.launch { store.setRowSpacing(it) } },
                    onShowIcons = { scope.launch { store.setShowIcons(it) } },
                    onShowBadges = { scope.launch { store.setShowBadges(it) } },
                    onShowOriginalColor = { scope.launch { store.setShowOriginalColor(it) } },
                    onMaxApps = { scope.launch { store.setMaxApps(it) } },
                    onEasterEgg = { scope.launch { store.setEasterEggEnabled(it) } },
                    onUnlockEasterEgg = { scope.launch { store.setEasterEggUnlocked() } },
                    onMeritSound = { scope.launch { store.setMeritSoundEnabled(it) } },
                    onMeritLabel = { scope.launch { store.setMeritLabel(it) } },
                    onAutoMeritEnabled = { v ->
                        scope.launch {
                            store.setAutoMeritEnabled(v)
                            // 开启自动积累时同步开启彩蛋总开关:
                            // 总开关关闭时自动积累静默,会让人以为功能失效(修过的坑)
                            if (v) store.setEasterEggEnabled(true)
                        }
                    },
                    onAutoMeritMaxS = { scope.launch { store.setAutoMeritMaxS(it) } },
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
            showSystem = data.showSystem,
            onShowSystemChange = { scope.launch { store.setShowSystem(it) } },
            favorites = data.favorites,
            onPick = { entry ->
                when (req) {
                    is PickerRequest.Add -> scope.launch {
                        val cur = data.entries
                        if (cur.none { it.component == entry.component } &&
                            cur.size < data.maxApps
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
                favorites = data.favorites,
                showSystem = data.showSystem,
                onShowSystemChange = { scope.launch { store.setShowSystem(it) } },
                onLaunch = { launch(repo, it) },
                onAddToHome = { entry ->
                    scope.launch {
                        val cur = data.entries
                        if (cur.none { it.component == entry.component } &&
                            cur.size < data.maxApps
                        ) {
                            store.setEntries(cur + StoredEntry(entry.component, null))
                        }
                    }
                },
                onToggleFavorite = { entry ->
                    scope.launch { store.toggleFavorite(entry.component, entry.component !in data.favorites) }
                },
                onDismiss = { drawerOpen = false },
            )
        }
    }

}

private fun launch(repo: AppRepository, entry: AppEntry) {
    // 主用户走 startActivity,分身经 LauncherApps.startMainActivity 定位到对应用户
    repo.launch(entry)
}
