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
    adding: Boolean,
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
        onRowClick = onPick,
        onDismiss = onDismiss,
    )
}
