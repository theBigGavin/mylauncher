package com.mylauncher.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mylauncher.badges.isBadgeListenerEnabled
import com.mylauncher.data.HomeStore
import kotlin.math.roundToInt

/** GitHub Octocat 徽标路径(simple-icons 的 24×24 视图)。 */
private const val GITHUB_MARK_PATH =
    "M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"

/**
 * 全屏设置页:长按主屏空白处打开。
 * Zune 风格:左对齐、大字号,白字直接铺在壁纸上,与桌面列表一致。
 */
@Composable
fun SettingsScreen(
    iconSize: Int,
    fontSize: Int,
    rowSpacing: Int,
    showIcons: Boolean,
    showBadges: Boolean,
    showOriginalColor: Boolean,
    wallpaperMode: String,
    customScale: Float,
    customOffsetX: Float,
    customOffsetY: Float,
    listHeightPercent: Int,
    listHeightPercentLandscape: Int,
    maxApps: Int,
    easterEggEnabled: Boolean,
    meritSoundEnabled: Boolean,
    onIconSize: (Int) -> Unit,
    onFontSize: (Int) -> Unit,
    onRowSpacing: (Int) -> Unit,
    onShowIcons: (Boolean) -> Unit,
    onShowBadges: (Boolean) -> Unit,
    onShowOriginalColor: (Boolean) -> Unit,
    onMaxApps: (Int) -> Unit,
    onEasterEgg: (Boolean) -> Unit,
    onMeritSound: (Boolean) -> Unit,
    onPickSystemWallpaper: () -> Unit,
    onListHeight: (form: String, value: Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val config = LocalConfiguration.current
    // 从系统设置返回(如设置默认桌面后)时重新检测默认桌面
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isDefaultLauncher = remember(resumeTick) {
        currentDefaultHome(context)?.packageName == context.packageName
    }
    val defaultLabel = remember(resumeTick) {
        currentDefaultHome(context)?.let { cn ->
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(cn.packageName, 0)
                ).toString()
            }.getOrNull()
        }
    }
    val listenerEnabled = remember { isBadgeListenerEnabled(context) }

    // 列表行距:离散档位 —— 按当前布局的可视高度反推行距,保证整行完整显示(不被边缘裁切)
    val baseRowHeight = appRowHeight(iconSize.dp, fontSize.sp).value
    val spacingOptions = remember(iconSize, fontSize, config.screenWidthDp, config.screenHeightDp) {
        val h = config.screenHeightDp.toFloat()
        val w = config.screenWidthDp.toFloat()
        val listHeight = if (w > h) {
            h - 96f // 横屏:列表上下各 48dp
        } else {
            // 竖屏:总高 - 顶部留白20% - 时钟(时间+日期) - 列表间距5% - 底部计数
            val time = minOf(120f, w * 0.26f)
            val date = (time * 0.32f).coerceIn(26f, 44f)
            h - 0.20f * h - (time + 10f + date) - 0.05f * h - 90f
        }
        listOf(12, 10, 8, 6).mapNotNull { rows ->
            val spacing = (listHeight / rows - baseRowHeight).roundToInt()
            if (spacing in 0..48) rows to spacing else null
        }
    }

    Box(Modifier.fillMaxSize()) {
        GlassPageBackground(
            wallpaperMode = wallpaperMode,
            modifier = Modifier.fillMaxSize(),
            customScale = customScale,
            customOffsetX = customOffsetX,
            customOffsetY = customOffsetY,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = LIST_H_MARGIN, end = LIST_H_MARGIN, bottom = 80.dp)
        ) {
            Spacer(Modifier.height((config.screenHeightDp * 0.10f).dp))
            BasicText(
                text = "设置",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = "长按主屏空白处打开本页 · 改动实时生效",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                ),
            )
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                SettingRow("图标大小") {
                    MiniSlider(
                        value = iconSize.toFloat(),
                        range = 24f..56f,
                        modifier = Modifier.width(150.dp),
                        onChange = { onIconSize((it / 2).roundToInt() * 2) },
                    )
                }
                SettingRow("字体大小") {
                    MiniSlider(
                        value = fontSize.toFloat(),
                        range = 18f..40f,
                        modifier = Modifier.width(150.dp),
                        onChange = { onFontSize(it.roundToInt()) },
                    )
                }
                SettingRow("列表高度(竖屏)") {
                    MiniSlider(
                        value = listHeightPercent.toFloat(),
                        range = 25f..55f,
                        modifier = Modifier.width(150.dp),
                        onChange = { onListHeight(HomeStore.WALLPAPER_FORM_PORTRAIT, it.roundToInt()) },
                    )
                }
                SettingRow("列表高度(横屏)") {
                    MiniSlider(
                        value = listHeightPercentLandscape.toFloat(),
                        range = 25f..100f,
                        modifier = Modifier.width(150.dp),
                        onChange = { onListHeight(HomeStore.WALLPAPER_FORM_LANDSCAPE, it.roundToInt()) },
                    )
                }
                SettingRow("列表行距") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        spacingOptions.forEach { (rows, spacing) ->
                            val selected = spacing == rowSpacing
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
                                    .clickable { onRowSpacing(spacing) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(
                                    text = "${rows}行",
                                    style = TextStyle(
                                        color = if (selected) Color(0xFF1E1E22) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                }
                SettingRow("桌面槽位数") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = "$maxApps",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                            ),
                        )
                        Spacer(Modifier.width(10.dp))
                        MiniSlider(
                            value = maxApps.toFloat(),
                            range = HomeStore.MIN_MAX_APPS.toFloat()..HomeStore.MAX_APPS.toFloat(),
                            modifier = Modifier.width(120.dp),
                            onChange = { onMaxApps(it.roundToInt()) },
                        )
                    }
                }
                SettingRow("显示图标") {
                    MiniSwitch(checked = showIcons, onChange = onShowIcons)
                }
                SettingRow("图标原彩") {
                    MiniSwitch(checked = showOriginalColor, onChange = onShowOriginalColor)
                }
                SettingRow("功德彩蛋(边缘滑入)") {
                    MiniSwitch(checked = easterEggEnabled, onChange = onEasterEgg)
                }
                SettingRow("木鱼音效") {
                    MiniSwitch(checked = meritSoundEnabled, onChange = onMeritSound)
                }
                SettingRow("通知角标") {
                    MiniSwitch(checked = showBadges, onChange = onShowBadges)
                }
                SettingRow("壁纸") {
                    BasicText(
                        text = when (wallpaperMode) {
                            HomeStore.WALLPAPER_CUSTOM -> "自定义(旧配置)"
                            HomeStore.WALLPAPER_SYSTEM -> "跟随系统"
                            else -> "内置几何"
                        },
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                        ),
                    )
                }
                TextButton(
                    text = "更换壁纸…",
                    onClick = onPickSystemWallpaper,
                    strong = false,
                )
                SettingRow("默认桌面") {
                    BasicText(
                        text = if (isDefaultLauncher) "已设为默认" else (defaultLabel ?: "未设置"),
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                        ),
                    )
                }
                if (showBadges && !listenerEnabled) {
                    TextButton(
                        text = "开启系统通知使用权(角标需要)",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            }
                        },
                        strong = false,
                    )
                }
                if (!isDefaultLauncher) {
                    TextButton(
                        text = "设为默认桌面",
                        onClick = {
                            // 打开系统"默认主屏幕应用"设置页 —— 能真正修改默认
                            // (角色请求对话框在部分国产 ROM 上失效;CHOOSER 不持久化)
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                            }
                        },
                        strong = true,
                    )
                }
                TextButton(
                    text = "恢复默认布局",
                    onClick = onReset,
                    strong = false,
                )
                TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    strong = false,
                )
                Spacer(Modifier.height(28.dp))
                // 页脚:版本号 + GitHub logo(点击打开仓库)
                val versionName = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "MyLauncher v${versionName ?: "?"}",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                        ),
                    )
                    Spacer(Modifier.width(14.dp))
                    val githubPath = remember {
                        PathParser().parsePathString(GITHUB_MARK_PATH).toPath()
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/theBigGavin/mylauncher"),
                                        )
                                    )
                                }
                            }
                            .padding(6.dp)
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val scale = size.minDimension / 24f
                            withTransform({
                                translate(
                                    left = (size.width - 24f * scale) / 2f,
                                    top = (size.height - 24f * scale) / 2f,
                                )
                                scale(scale, scale, pivot = Offset.Zero)
                            }) {
                                drawPath(githubPath, Color.White.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 简单文本按钮:白字,无边框无底色,与设置页整体风格一致。 */
@Composable
private fun TextButton(
    text: String,
    onClick: () -> Unit,
    strong: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        style = TextStyle(
            color = if (strong) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 1.sp,
            shadow = textShadow,
        ),
    )
}

/**
 * 拉起系统壁纸选择器:优先 ACTION_SET_WALLPAPER;
 * ROM 不支持时降级 ACTION_CHANGE_LIVE_WALLPAPER;再不支持则 Toast 提示。
 */

/**
 * 当前默认桌面的 ComponentName。
 * 注意:必须用 resolveActivity —— 它遵循系统 HOME 角色/默认解析;
 * queryIntentActivities 会返回全部候选(多个),无法判断谁才是默认。
 */
private fun currentDefaultHome(context: Context): ComponentName? {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }
    return runCatching {
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.let { ComponentName(it.packageName, it.name) }
    }.getOrNull()
}

/** 设置行:左对齐大字号标签 + 右侧控件(风格同桌面列表)。 */
@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            ),
        )
        content()
    }
}
