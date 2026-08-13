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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mylauncher.badges.BadgeStore
import com.mylauncher.data.AppEntry
import com.mylauncher.data.AppRepository
import com.mylauncher.data.DefaultApps
import com.mylauncher.data.HomeStore
import com.mylauncher.data.StoredEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface PickerRequest {
    data class Replace(val index: Int) : PickerRequest
    data object Add : PickerRequest
}

/** 主屏总装配:方向感知布局 + 全部浮层(改名 / 选择器 / 设置 / 抽屉)。 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    val store = remember { HomeStore(context) }
    val scope = rememberCoroutineScope()

    val allApps by repo.apps.collectAsState()
    val homeData by store.data.collectAsState(initial = null)
    val badgeCounts by BadgeStore.counts.collectAsState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { repo.refresh() }
    }

    // 监听安装 / 卸载 / 更新,刷新应用列表
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                repo.refresh()
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val topSpace = maxHeight * 0.20f
        val listSpace = maxHeight * 0.05f
        // 背景:内置几何壁纸(默认)或跟随系统壁纸(FLAG_SHOW_WALLPAPER + 暗纱)
        val systemMode = data?.wallpaperMode == HomeStore.WALLPAPER_SYSTEM
        ApplyShowWallpaperFlag(enabled = systemMode)
        // 手势挂在背景层上:长按空白开设置(行内长按由行自己处理,行更深先收到事件)、上滑开抽屉
        val bgModifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        if (picker == null && !drawerOpen && renameIndex < 0 && !showSettings) {
                            showSettings = true
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                var upward = false
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (upward && picker == null && renameIndex < 0 && !showSettings && !drawerOpen) {
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
        } else {
            Wallpaper(bgModifier)
        }

        if (data != null) {
            val iconSize = data.iconSizeDp.dp
            val fontSize = data.fontSizeSp.sp
            val rowSpacing = data.rowSpacingDp.dp

            if (!landscape) {
                // 竖屏:居中超大时钟 + 竖行列表 + 底部计数
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(topSpace))
                    ClockWidget(landscape = false, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(listSpace))
                    AppList(
                        items = items,
                        iconSize = iconSize,
                        fontSize = fontSize,
                        rowSpacing = rowSpacing,
                        showIcons = data.showIcons,
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
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    BasicText(
                        text = "— ${items.size} / ${HomeStore.MAX_APPS} —",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 48.dp),
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            } else {
                // 横屏:时钟左下,列表靠右单列(图标一列垂线、名称右对齐)
                // 列宽固定 = 内容+边距;左滑只让选中行内容左移,列不伸缩
                val listWidth = with(LocalDensity.current) {
                    (iconSize.toPx() + (fontSize * 5.5f).toPx() + 18.dp.toPx() +
                        20.dp.toPx() + 180.dp.toPx() + 12.dp.toPx() + 12.dp.toPx()).toDp()
                }
                Box(Modifier.fillMaxSize()) {
                    ClockWidget(
                        landscape = true,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 80.dp, bottom = 80.dp),
                    )
                    AppList(
                        items = items,
                        iconSize = iconSize,
                        fontSize = fontSize,
                        rowSpacing = rowSpacing,
                        showIcons = data.showIcons,
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
                            .fillMaxHeight()
                            .width(listWidth)
                            .padding(start = 20.dp, end = 180.dp, top = 48.dp, bottom = 48.dp)
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
                    showBadges = data.showBadges,
                    wallpaperMode = data.wallpaperMode,
                    onIconSize = { scope.launch { store.setIconSize(it) } },
                    onFontSize = { scope.launch { store.setFontSize(it) } },
                    onRowSpacing = { scope.launch { store.setRowSpacing(it) } },
                    onShowIcons = { scope.launch { store.setShowIcons(it) } },
                    onShowBadges = { scope.launch { store.setShowBadges(it) } },
                    onWallpaperMode = { scope.launch { store.setWallpaperMode(it) } },
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
