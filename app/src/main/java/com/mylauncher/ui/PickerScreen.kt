package com.mylauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylauncher.data.AppEntry
import com.mylauncher.icons.rememberMonoIcon

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
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Wallpaper(Modifier.fillMaxSize(), showMiddleLine = false)

        // 空白处点击关闭(行会优先消费自己的点击)
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )

        val config = LocalConfiguration.current
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height((config.screenHeightDp * 0.07f).dp))
            BasicText(
                text = "选择应用",
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
                text = "共 ${apps.size} 个应用 · 点选即${if (adding) "添加" else "替换"}",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.height((config.screenHeightDp * 0.03f).dp))

            val rowHeight = appRowHeight(iconSize, fontSize)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(apps, key = { it.component }) { app ->
                    val icon = rememberMonoIcon(app, iconSize)
                    AppRow(
                        name = app.label,
                        icon = icon,
                        iconSize = iconSize,
                        fontSize = fontSize,
                        showIcons = showIcons,
                        landscape = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .clickable { onPick(app) }
                            .padding(horizontal = 26.dp),
                    )
                }
            }
        }
    }
}
