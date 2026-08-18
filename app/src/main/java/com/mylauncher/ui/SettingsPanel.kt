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
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mylauncher.badges.isBadgeListenerEnabled
import com.mylauncher.R
import com.mylauncher.data.HomeStore
import com.mylauncher.data.LeaderboardApi
import com.mylauncher.data.LeaderboardData
import com.mylauncher.ui.share.ShareImageActivity
import kotlinx.coroutines.launch
import java.util.Locale
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
    easterEggUnlocked: Boolean,
    meritSoundEnabled: Boolean,
    meritLabel: String,
    autoMeritEnabled: Boolean,
    autoMeritMaxS: Int,
    fastestKnockGapMs: Int,
    meritPeak: Int,
    leaderboardNickname: String,
    onLeaderboardNickname: (String) -> Unit,
    onIconSize: (Int) -> Unit,
    onFontSize: (Int) -> Unit,
    onRowSpacing: (Int) -> Unit,
    onShowIcons: (Boolean) -> Unit,
    onShowBadges: (Boolean) -> Unit,
    onShowOriginalColor: (Boolean) -> Unit,
    onMaxApps: (Int) -> Unit,
    onEasterEgg: (Boolean) -> Unit,
    onUnlockEasterEgg: () -> Unit,
    onMeritSound: (Boolean) -> Unit,
    onMeritLabel: (String) -> Unit,
    onAutoMeritEnabled: (Boolean) -> Unit,
    onAutoMeritMaxS: (Int) -> Unit,
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
    // 与默认桌面检测一样:从系统通知使用权设置页返回时重查,
    // 否则授权后入口不会立即隐藏(要重进设置页才刷新 —— 修过的坑)
    val listenerEnabled = remember(resumeTick) { isBadgeListenerEnabled(context) }

    // 列表行距:连续滑条(与其它尺寸类设置一致),不再用离散档位

    Box(Modifier.fillMaxSize()) {
        GlassPageBackground(
            wallpaperMode = wallpaperMode,
            modifier = Modifier.fillMaxSize(),
            customScale = customScale,
            customOffsetX = customOffsetX,
            customOffsetY = customOffsetY,
        )
        SubPage(
            title = "设置",
            subtitle = "长按主屏空白处打开本页 · 改动实时生效",
            onDismiss = onDismiss,
        ) {
            // 滚动内容:与桌面/抽屉/选择器的行起点边距统一(PORTRAIT_ROW_MARGIN)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = PORTRAIT_ROW_MARGIN, end = PORTRAIT_ROW_MARGIN, bottom = 80.dp)
            ) {
                Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // 分组:外观 → 桌面 → 通知 → 彩蛋 → 其他(高频操作靠前,系统入口跟随其开关)
                SettingSection("外观")
                SettingRow("图标大小") {
                    SliderSetting(iconSize, 24f..56f) { onIconSize(it / 2 * 2) }
                }
                SettingRow("字体大小") {
                    SliderSetting(fontSize, 18f..40f) { onFontSize(it) }
                }
                SettingRow("列表行距") {
                    SliderSetting(rowSpacing, 0f..10f) { onRowSpacing(it) }
                }
                SettingRow("列表高度(竖屏)") {
                    SliderSetting(listHeightPercent, 25f..55f) {
                        onListHeight(HomeStore.WALLPAPER_FORM_PORTRAIT, it)
                    }
                }
                SettingRow("列表高度(横屏)") {
                    SliderSetting(listHeightPercentLandscape, 25f..100f) {
                        onListHeight(HomeStore.WALLPAPER_FORM_LANDSCAPE, it)
                    }
                }
                SettingRow("桌面槽位数") {
                    SliderSetting(
                        maxApps,
                        HomeStore.MIN_MAX_APPS.toFloat()..HomeStore.MAX_APPS.toFloat(),
                    ) { onMaxApps(it) }
                }
                SettingRow("显示图标") {
                    MiniSwitch(checked = showIcons, onChange = onShowIcons)
                }
                SettingRow("图标原彩") {
                    MiniSwitch(checked = showOriginalColor, onChange = onShowOriginalColor)
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

                SettingSection("通知")
                SettingRow("通知角标") {
                    MiniSwitch(checked = showBadges, onChange = onShowBadges)
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

                // 彩蛋设置分组:默认隐藏,连点页脚版本号 5 次解锁后才显示
                if (easterEggUnlocked) {
                    SettingSection("彩蛋")
                    SettingRow("功德彩蛋") {
                        MiniSwitch(checked = easterEggEnabled, onChange = onEasterEgg)
                    }
                    SettingRow("敲击音效") {
                        MiniSwitch(checked = meritSoundEnabled, onChange = onMeritSound)
                    }
                    // 自定义功德文字:冒泡显示"文字+N",清空后冒泡回退默认"功德"。
                    // 本地编辑态:允许编辑中短暂空白(直接绑定读取值会在删空瞬间被默认值回填,删不干净)
                    var labelText by remember(meritLabel) { mutableStateOf(meritLabel) }
                    SettingRow("功德文字") {
                        Box(
                            Modifier
                                .width(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            BasicTextField(
                                value = labelText,
                                onValueChange = {
                                    labelText = it.take(6)
                                    onMeritLabel(labelText)
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 1.sp,
                                ),
                                cursorBrush = SolidColor(Color.White),
                            )
                        }
                    }
                    SettingRow("自动积累功德") {
                        MiniSwitch(checked = autoMeritEnabled, onChange = onAutoMeritEnabled)
                    }
                    if (autoMeritEnabled) {
                        // 连续积累时长:开启后每秒 +1,最多连续积累这么多秒,
                        // 到期暂停,回到桌面/解锁重新开始一轮
                        SettingRow("连续积累时长(秒)") {
                            SliderSetting(
                                autoMeritMaxS,
                                HomeStore.MIN_AUTO_MERIT_INTERVAL_S.toFloat()..
                                    HomeStore.MAX_AUTO_MERIT_INTERVAL_S.toFloat(),
                            ) { onAutoMeritMaxS(it) }
                        }
                    }
                    // 战绩展示:最快手速(敲击间隔纪录)与历史最高单日功德
                    SettingRow("最快手速") {
                        BasicText(
                            text = if (fastestKnockGapMs > 0) {
                                String.format(java.util.Locale.US, "%.1f 次/秒", 1000f / fastestKnockGapMs)
                            } else {
                                "—"
                            },
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                            ),
                        )
                    }
                    SettingRow("每日最高功德") {
                        BasicText(
                            text = "$meritPeak",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                            ),
                        )
                    }
                    // 手速排行榜小节(20b):昵称 / 上传 / 榜单 top10 / 我的名次 / 隐私说明;
                    // 手速分享图按钮移入小节下方(20a 组件,复用 ShareImageActivity)
                    LeaderboardSection(
                        fastestKnockGapMs = fastestKnockGapMs,
                        nickname = leaderboardNickname,
                        onNicknameChange = onLeaderboardNickname,
                    )
                }

                SettingSection("其他")
                SettingRow("默认桌面") {
                    BasicText(
                        text = if (isDefaultLauncher) "已设为默认" else (defaultLabel ?: "未设置"),
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                        ),
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
                        strong = false,
                    )
                }
                TextButton(
                    text = "恢复默认布局",
                    onClick = onReset,
                    strong = false,
                )
                Spacer(Modifier.height(28.dp))
                // 页脚:版本号 + GitHub logo(点击打开仓库);连点版本号 5 次解锁彩蛋设置分组
                val versionName = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull()
                var versionTaps by remember { mutableIntStateOf(0) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "MyLauncher v${versionName ?: "?"}",
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                versionTaps++
                                if (versionTaps >= 5 && !easterEggUnlocked) {
                                    onUnlockEasterEgg()
                                }
                            }
                            .padding(vertical = 6.dp),
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
}

/** 简单文本按钮:白字,无边框无底色,与设置页整体风格一致。 */
@Composable
private fun TextButton(
    text: String,
    onClick: () -> Unit,
    strong: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        style = TextStyle(
            color = when {
                !enabled -> Color.White.copy(alpha = 0.30f)
                strong -> Color.White
                else -> Color.White.copy(alpha = 0.7f)
            },
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

/** 设置分组标题:小号大写感标签,与抽屉副标题同一风格。 */
@Composable
private fun SettingSection(title: String) {
    BasicText(
        text = title,
        modifier = Modifier.padding(top = 26.dp, bottom = 4.dp),
        style = TextStyle(
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        ),
    )
}

/** 滑条设置行内容:当前值文本 + 滑条 —— 所有滑条统一显示配置值(与桌面槽位数一致)。 */
@Composable
private fun SliderSetting(
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = "$value",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            ),
        )
        Spacer(Modifier.width(10.dp))
        MiniSlider(
            value = value.toFloat(),
            range = range,
            modifier = Modifier.width(120.dp),
            onChange = { onChange(it.roundToInt()) },
        )
    }
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

// ───────────────────────── 手速排行榜小节(20b) ─────────────────────────

/** 榜单 UI 状态:加载中 / 失败(点击重试) / 就绪。 */
private sealed interface LbState {
    data object Loading : LbState
    data object Error : LbState
    data class Ready(val data: LeaderboardData) : LbState
}

/**
 * 设置页彩蛋组「手速排行榜」小节:
 * 昵称输入框(本地持久化,首启自动生成随机名零摩擦) / 上传按钮(gapMs>=40 && 会话连击 samples>=10 才可用)
 * / 榜单 top10(名次·昵称·次每秒,自己高亮) / 「我的名次」行 / 隐私说明 / 生成分享图入口(20a 组件)。
 * 网络失败静默降级:榜单显示「加载失败,点击重试」;上传失败 Toast 提示。
 */
@Composable
private fun LeaderboardSection(
    fastestKnockGapMs: Int,
    nickname: String,
    onNicknameChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 昵称本地编辑态(与功德文字同模式:允许编辑中短暂空白,失焦/上传以 trim 为准)
    var nickText by remember(nickname) { mutableStateOf(nickname) }
    var lbUploading by remember { mutableStateOf(false) }
    var lbState by remember { mutableStateOf<LbState>(LbState.Loading) }

    fun reload() {
        scope.launch {
            lbState = LbState.Loading
            val nick = nickText.trim().takeIf { it.isNotEmpty() }
            val d = LeaderboardApi.fetchLeaderboard(nick)
            lbState = if (d != null) LbState.Ready(d) else LbState.Error
        }
    }

    // 进入小节时拉取一次;「加载失败,点击重试」手动重拉
    LaunchedEffect(Unit) { reload() }

    SettingSection(context.getString(R.string.lb_section))

    // 昵称输入框(≤16 字符,与榜单契约一致;HomeStore.setLeaderboardNickname 内部再 trim+限长)
    SettingRow(context.getString(R.string.lb_nickname_label)) {
        Box(
            Modifier
                .width(160.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = nickText,
                onValueChange = {
                    nickText = it.take(16)
                    onNicknameChange(nickText)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp,
                ),
                cursorBrush = SolidColor(Color.White),
            )
        }
    }

    // 上传按钮:读历史最快纪录 + 会话连击样本数(不足置灰并提示)。
    // 纪录存在即可上传(Gavin 2026-08-19:完全放开下限,娱乐榜)——客户端防重入
    // 下限 20ms 保证 App 产出的纪录天然 ≥20ms,服务端靠 rate/gapMs 互验兜底伪造
    val gapMs = fastestKnockGapMs
    val samples = KnockSound.samples
    val canUpload = gapMs > 0 && samples >= 10 && !lbUploading
    TextButton(
        text = if (lbUploading) context.getString(R.string.lb_uploading)
        else context.getString(R.string.lb_upload),
        onClick = {
            // 昵称兜底:空则生成随机名(与首启一致,零摩擦直接可传),并同步输入框与持久化
            val finalNick = nickText.trim().ifEmpty {
                context.getString(R.string.share_default_nickname) + "#" + (1000..9999).random()
            }
            nickText = finalNick
            onNicknameChange(finalNick)
            scope.launch {
                lbUploading = true
                // rate 提交精确值(1000/gapMs),1 位小数会被服务端互验拒绝(实测坑)
                val resp = LeaderboardApi.submit(finalNick, 1000f / gapMs, gapMs, samples)
                lbUploading = false
                if (resp != null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.lb_upload_ok, resp.rank, resp.totalPlayers),
                        Toast.LENGTH_SHORT,
                    ).show()
                    // 响应自带 top10:直接刷新榜单,myRank 用本次成绩名次
                    lbState = LbState.Ready(LeaderboardData(resp.entries, resp.totalPlayers, resp.rank))
                } else {
                    Toast.makeText(context, context.getString(R.string.lb_upload_failed), Toast.LENGTH_SHORT).show()
                }
            }
        },
        enabled = canUpload,
        strong = false,
    )
    // 不足门槛提示(仅置灰时显示,按原因区分)+ 隐私说明(常显)
    if (!canUpload && !lbUploading) {
        BasicText(
            text = if (samples < 10) context.getString(R.string.lb_upload_hint)
            else context.getString(R.string.lb_rate_abnormal),
            modifier = Modifier.padding(top = 2.dp),
            style = TextStyle(
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
            ),
        )
    }
    BasicText(
        text = context.getString(R.string.lb_privacy),
        modifier = Modifier.padding(top = 2.dp),
        style = TextStyle(
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 12.sp,
        ),
    )

    // 榜单 top10 + 我的名次
    when (val st = lbState) {
        LbState.Loading -> HintRow(context.getString(R.string.lb_loading))
        LbState.Error -> TextButton(
            text = context.getString(R.string.lb_load_failed),
            onClick = { reload() },
            strong = false,
        )
        is LbState.Ready -> {
            if (st.data.entries.isEmpty()) {
                HintRow(context.getString(R.string.lb_empty))
            } else {
                val myNick = nickText.trim()
                st.data.entries.forEach { e ->
                    val mine = e.nickname == myNick
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            text = "${e.rank}",
                            modifier = Modifier.width(28.dp),
                            style = TextStyle(
                                color = if (mine) Color.White else Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                        BasicText(
                            text = e.nickname,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = if (mine) Color.White else Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                        BasicText(
                            text = context.getString(
                                R.string.lb_rate_format,
                                String.format(Locale.US, "%.1f", e.rate),
                            ),
                            style = TextStyle(
                                color = if (mine) Color.White else Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
            // 我的名次(未上榜显示「未上榜」)
            val rankText = st.data.myRank?.let { "$it" } ?: context.getString(R.string.lb_not_on_board)
            BasicText(
                text = context.getString(R.string.lb_my_rank, rankText),
                modifier = Modifier.padding(top = 8.dp),
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                ),
            )
        }
    }

    // 生成分享图入口(20a 组件;A 批在「每日最高功德」下方,移入排行榜小节)
    TextButton(
        text = context.getString(R.string.share_generate),
        onClick = {
            runCatching {
                context.startActivity(Intent(context, ShareImageActivity::class.java))
            }
        },
        strong = false,
    )
}

/** 榜单区占位小字(加载中 / 空榜)。 */
@Composable
private fun HintRow(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(vertical = 8.dp),
        style = TextStyle(
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 14.sp,
        ),
    )
}
