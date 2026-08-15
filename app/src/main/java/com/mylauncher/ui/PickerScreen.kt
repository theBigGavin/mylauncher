package com.mylauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.mylauncher.data.AppEntry

/** 全量应用选择器:与主屏同款行样式,点选即生效;返回键 / 点空白处关闭。 */
@Composable
fun PickerScreen(
    apps: List<AppEntry>,
    iconSize: Dp,
    fontSize: TextUnit,
    showIcons: Boolean,
    showOriginalColor: Boolean,
    wallpaperMode: String,
    adding: Boolean,
    showSystem: Boolean,
    onShowSystemChange: (Boolean) -> Unit,
    favorites: Set<String>,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AppListOverlay(
        title = "选择应用",
        subtitle = if (adding) "点选添加" else "点选替换",
        apps = apps,
        iconSize = iconSize,
        fontSize = fontSize,
        showIcons = showIcons,
        showOriginalColor = showOriginalColor,
        wallpaperMode = wallpaperMode,
        showSystem = showSystem,
        onShowSystemChange = onShowSystemChange,
        // 收藏应用置顶 + 行首实心星标(与抽屉一致的收藏特性;非收藏不显示空星标)
        favorites = favorites,
        onRowClick = onPick,
        onDismiss = onDismiss,
    )
}
