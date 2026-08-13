package com.mylauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mylauncher.data.AppRepository
import com.mylauncher.data.HomeStore
import com.mylauncher.data.StoredEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private sealed interface PickerRequest {
    data class Replace(val index: Int) : PickerRequest
    data object Add : PickerRequest
}

/** 主屏总装配:方向感知布局 + 全部浮层(菜单 / 改名 / 选择器 / 设置)。 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    val store = remember { HomeStore(context) }
    val scope = rememberCoroutineScope()

    val allApps by repo.apps.collectAsState()
    val homeData by store.data.collectAsState(initial = null)

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

    // 首次启动:默认填充前 12 个可启动应用(按名称排序)
    LaunchedEffect(apps, data?.initialized) {
        if (apps != null && data != null && !data.initialized && apps.isNotEmpty()) {
            store.setEntries(apps.take(12).map { StoredEntry(it.component, null) })
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
    var menuIndex by remember { mutableIntStateOf(-1) }
    var menuPos by remember { mutableStateOf(IntOffset.Zero) }
    var renameIndex by remember { mutableIntStateOf(-1) }
    var showSettings by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val topSpace = maxHeight * 0.09f
        val listSpace = maxHeight * 0.045f
        Wallpaper(Modifier.fillMaxSize())

        if (data != null) {
            val iconSize = data.iconSizeDp.dp
            val fontSize = data.fontSizeSp.sp

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
                        showIcons = data.showIcons,
                        landscape = false,
                        onLaunch = { launch(context, repo, it) },
                        onLongPressMenu = { idx, pos ->
                            menuIndex = idx
                            menuPos = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                        },
                        onMove = { from, to ->
                            scope.launch { store.setEntries(moveEntry(data.entries, from, to)) }
                        },
                        onAdd = { picker = PickerRequest.Add },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    BasicText(
                        text = "— ${items.size} / ${HomeStore.MAX_APPS} —",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 16.dp),
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
                Box(Modifier.fillMaxSize()) {
                    ClockWidget(
                        landscape = true,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 30.dp, bottom = 22.dp),
                    )
                    AppList(
                        items = items,
                        iconSize = iconSize,
                        fontSize = fontSize,
                        showIcons = data.showIcons,
                        landscape = true,
                        onLaunch = { launch(context, repo, it) },
                        onLongPressMenu = { idx, pos ->
                            menuIndex = idx
                            menuPos = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                        },
                        onMove = { from, to ->
                            scope.launch { store.setEntries(moveEntry(data.entries, from, to)) }
                        },
                        onAdd = { picker = PickerRequest.Add },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.52f)
                            .padding(horizontal = 30.dp, vertical = 18.dp),
                    )
                }
            }

            // 右下角设置齿轮
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { showSettings = !showSettings },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "⚙",
                    style = TextStyle(color = Color.White, fontSize = 19.sp),
                )
            }

            // ---- 浮层 ----
            if (menuIndex >= 0 && menuIndex < items.size) {
                LongPressMenu(
                    positionInWindow = menuPos,
                    onReplace = {
                        picker = PickerRequest.Replace(menuIndex)
                        menuIndex = -1
                    },
                    onRename = {
                        renameIndex = menuIndex
                        menuIndex = -1
                    },
                    onRemove = {
                        val i = menuIndex
                        menuIndex = -1
                        scope.launch {
                            val list = data.entries.toMutableList()
                            if (i < list.size) {
                                list.removeAt(i)
                                store.setEntries(list)
                            }
                        }
                    },
                    onDismiss = { menuIndex = -1 },
                )
            }

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
                SettingsPanel(
                    iconSize = data.iconSizeDp,
                    fontSize = data.fontSizeSp,
                    showIcons = data.showIcons,
                    onIconSize = { scope.launch { store.setIconSize(it) } },
                    onFontSize = { scope.launch { store.setFontSize(it) } },
                    onShowIcons = { scope.launch { store.setShowIcons(it) } },
                    onReset = {
                        showSettings = false
                        scope.launch {
                            store.resetAll(
                                (apps ?: emptyList()).take(12).map { StoredEntry(it.component, null) }
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
}

private fun moveEntry(entries: List<StoredEntry>, from: Int, to: Int): List<StoredEntry> {
    val list = entries.toMutableList()
    val e = list.removeAt(from)
    list.add(to.coerceIn(0, list.size), e)
    return list
}

private fun launch(context: Context, repo: AppRepository, item: HomeItem) {
    runCatching { context.startActivity(repo.launchIntent(item.app)) }
}
