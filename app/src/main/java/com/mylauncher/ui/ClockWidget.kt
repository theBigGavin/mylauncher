package com.mylauncher.ui

import android.util.Log
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 功德气泡调试开关(与 HomeScreen.kt 的 DEBUG_MERIT 同步):改 true 重新安装后,
 * logcat -s MyLauncher 可见气泡全链路证据 —— 追加(seq/id/长度)/动画完成/协程取消兜底移除。
 * 实机对比修复前后:自动积累 10s 观察 bubble-finished 与 done-remove 是否一一对应、无 cancel 残留。
 */
private const val DEBUG_MERIT = false

/** 一个功德气泡:从小放大扩散、白→透明消失,位置/字号随机(不影响布局)。 */
data class MeritBubbleData(val id: Int, val count: Int, val label: String = "功德")

/** 实时时钟:HH:mm + "星期X · M月D日"。竖屏居中超大细体;横屏左下较小。 */
@Composable
fun ClockWidget(
    landscape: Boolean,
    modifier: Modifier = Modifier,
    /** 横屏时时钟可用的最大宽度(dp,由调用方按屏幕宽 - 列表宽 - 边距算出);竖屏忽略。 */
    availableWidthDp: Float? = null,
    /** 功德彩蛋气泡(边缘滑入触发)。 */
    meritBubbles: List<MeritBubbleData> = emptyList(),
    onMeritBubbleDone: (Int) -> Unit = {},
) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            delay(1000)
        }
    }

    val time = "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
    val week = "日一二三四五六"[now.get(Calendar.DAY_OF_WEEK) - 1]
    val date = "星期$week · ${now.get(Calendar.MONTH) + 1}月${now.get(Calendar.DAY_OF_MONTH)}日"

    val config = LocalConfiguration.current
    // 竖屏对应 CSS min(26vw, 120px);横屏:高度基准(至少 84sp / 屏高 20%)与
    // 可用宽度基准("00:00" 约 2.35em:细体数字 ~0.5em、冒号 ~0.35em)取小,防与列表重叠
    val timeSize = if (landscape) {
        val byHeight = maxOf(84f, config.screenHeightDp * 0.20f)
        if (availableWidthDp != null) {
            minOf(byHeight, availableWidthDp / 2.35f).coerceAtLeast(40f)
        } else {
            byHeight
        }
    } else {
        minOf(120f, config.screenWidthDp * 0.26f)
    }
    // 日期约为时间字号 1/3,宽度与时间大致对齐;加大字间距配 Zune 风格
    val dateSize = if (landscape) {
        (timeSize * 0.32f).coerceIn(22f, 48f)
    } else {
        (timeSize * 0.32f).coerceIn(26f, 44f)
    }

    // 记录时钟组件在窗口中的位置(气泡锚点需从窗口坐标换算成组件局部坐标)
    var windowX by remember { mutableStateOf(0f) }
    var windowY by remember { mutableStateOf(0f) }
    // 记录时间文字顶部中心在窗口中的位置:功德气泡从时间文字(HH:mm)顶部冒泡。
    // 不用时钟 Box 的 TopCenter —— 横屏时间文字 Start 对齐,Box 中心会偏右(旧实现的坑)
    var timeAnchorX by remember { mutableStateOf(0f) }
    var timeAnchorY by remember { mutableStateOf(0f) }
    BoxWithConstraints(
        modifier.onGloballyPositioned {
            val pos = it.positionInWindow()
            windowX = pos.x
            windowY = pos.y
        },
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            // 点击时间打开时间应用(闹钟/时钟),点击日期打开日历应用;无对应应用时静默
            val context = LocalContext.current
            BasicText(
                text = time,
                modifier = Modifier
                    .onGloballyPositioned {
                        val pos = it.positionInWindow()
                        timeAnchorX = pos.x + it.size.width / 2f
                        timeAnchorY = pos.y  // 锚点 = 时间文字顶部
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                        }
                    },
                style = TextStyle(
                    color = Color.White,
                    fontSize = timeSize.sp,
                    fontWeight = FontWeight.Thin,
                    letterSpacing = (-1).sp,
                    lineHeight = timeSize.sp,
                ),
            )
            Spacer(Modifier.height(if (landscape) 6.dp else 10.dp))
            BasicText(
                text = date,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        runCatching {
                            // 用日历专属类别,ACTION_VIEW+CONTENT_URI 会被通用处理器抢
                            // (OPPO 实测弹出 信息/Google/LibChecker,全是错的应用)
                            context.startActivity(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
                            )
                        }
                    },
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = dateSize.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = if (landscape) 3.sp else 4.sp,
                    shadow = textShadow,
                ),
            )
        }
        // 功德气泡层:matchParentSize 让整层铺满时钟可用区域但「不参与根 Box 测量」——
        // 气泡文字(「功德+N」)有真实测量尺寸,graphicsLayer 缩放只是绘制变换不改变测量;
        // 旧实现直接把气泡作为根 Box 子项(align(TopStart) 只影响放置、不影响测量),
        // 冒泡一出现就把 wrap-content 的根 Box 撑大:竖屏 Column 里 ClockWidget 变高直接
        // 推挤下方 AppList,横屏 BottomStart 对齐下根 Box 变高顶动时钟位置。
        // matchParentSize 的子项用父级约束测量、尺寸不贡献给父容器,冒泡对布局零影响。
        if (meritBubbles.isNotEmpty()) {
            val clockPx = with(LocalDensity.current) { timeSize.sp.toPx() }
            // 内层 Box 的 lambda 接收者是 BoxScope,解析不到外层 BoxWithConstraintsScope 的
            // maxWidth,这里提前捕获成局部变量(内层只依赖局部量,不依赖外层隐式接收者)
            val bubbleAreaW = maxWidth
            Box(Modifier.matchParentSize()) {
                meritBubbles.forEach { bubble ->
                    // 必须用 key 隔离:无 key 时前一个气泡完成后,后一个会复用其组合槽位
                    // (继承已完成的 Animatable),动画永不运行、onDone 永不触发 —— 气泡变成屏幕外透明态的僵尸
                    key(bubble.id) {
                        MeritBubble(
                            bubble = bubble,
                            clockFontSizePx = clockPx,
                            areaW = bubbleAreaW,
                            // 从时间文字处冒泡,向屏幕顶端扩散
                            anchorWindowX = timeAnchorX,
                            anchorWindowY = timeAnchorY,
                            rootWindowX = windowX,
                            rootWindowY = windowY,
                            onDone = { onMeritBubbleDone(bubble.id) },
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
            }
        }
    }
}

/** 单个功德气泡:从时间文字顶部冒泡,沿选定方向(向上斜线)扩散 + 放大 + 白→透明消失。 */
@Composable
private fun MeritBubble(
    bubble: MeritBubbleData,
    clockFontSizePx: Float,
    areaW: Dp,
    /** 锚点(时间文字顶部)在窗口中的 X/Y。 */
    anchorWindowX: Float,
    anchorWindowY: Float,
    /** 时钟组件原点在窗口中的 X/Y(锚点换算组件局部坐标用)。 */
    rootWindowX: Float,
    rootWindowY: Float,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // 随机参数必须 remember:组合期每次重算会让气泡在动画中途跳变(注释里"定下后不再变"的保证)
    val randomSpec = remember {
        // 字号固定为最终大小(时钟字号的 70%~90% 随机,定下后不再变);
        // 动画只用 graphicsLayer 缩放整体放大,不修改字体大小
        val endSize = clockFontSizePx * (0.70f + Random.nextFloat() * 0.20f)
        val startScale = 0.30f + Random.nextFloat() * 0.30f // 初始缩放随机(0.3~0.6)
        // 起始水平位置:时间文字中心附近(时钟中间 1/3 范围内随机)
        val startXPx = (Random.nextFloat() * 2f - 1f) * with(density) { areaW.toPx() } / 6f
        // 扩散方向:向上偏左/偏右的斜线(±25° 随机,选定后不变,不左右乱晃)
        val angleDeg = (Random.nextFloat() * 2f - 1f) * 25f
        val rad = Math.toRadians(angleDeg.toDouble())
        val dirX = sin(rad).toFloat()
        val dirY = -cos(rad).toFloat()
        // 扩散距离:锚点(时间文字)到屏幕顶端的距离(随机比例),冒泡升到屏幕顶端附近
        val dist = anchorWindowY * (0.8f + Random.nextFloat() * 0.4f)
        BubbleSpec(endSize, startScale, startXPx, dirX, dirY, dist)
    }
    val progress = remember { Animatable(0f) }
    // 贝塞尔曲线控制速度:快起慢收(冒泡感),运动平滑无抖动
    val bubbleEasing = CubicBezierEasing(0.0f, 0.45f, 0.25f, 1.0f)
    // 气泡文本宽度(用于把气泡水平居中于锚点)
    var widthPx by remember { mutableStateOf(0f) }
    // 字号随进度增长(替代 graphicsLayer 缩放):大字号文字 + 图层缩放 + 非中心缩放原点
    // 在部分 ROM 上层光栅化尺寸会算错,文字只剩上半部分(修过的坑);
    // 字号动画每次只重排这一个文本,开销可忽略,文字始终按自然尺寸渲染
    val animatedSize =
        randomSpec.endSize * (randomSpec.startScale + (1f - randomSpec.startScale) * progress.value)
    // onDone 经 rememberUpdatedState 取最新实例:重组后动画协程(LaunchedEffect(Unit) 不重启)
    // 调用的仍是组合期捕获的旧 lambda —— delegate 读写本身动态,但统一走最新引用更稳
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        try {
            progress.animateTo(1f, tween(durationMillis = 1300, easing = bubbleEasing))
            if (DEBUG_MERIT) Log.d("MyLauncher", "Merit[bubble-finished]: id=${bubble.id}")
            currentOnDone()
        } catch (e: CancellationException) {
            // 动画协程被取消 = 本气泡离开组合树(横竖屏切换/内屏形态变化/父级分支切换)。
            // 若不兜底移除,onDone 永不执行 → 气泡永久残留在 meritBubbles 列表里,
            // 自动积累每秒新增放大成「越积越多」(修复的坑)。
            if (DEBUG_MERIT) Log.d("MyLauncher", "Merit[bubble-canceled]: id=${bubble.id} -> remove")
            currentOnDone()
            throw e
        }
    }
    Box(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .graphicsLayer {
                // 图层只做平移与透明(不做缩放/旋转,避免文字光栅化被层变换裁切)
                val p = progress.value
                // 锚点 = 时间文字顶部(减半宽使气泡水平居中于锚点),再沿选定方向扩散
                translationX = (anchorWindowX - rootWindowX) + randomSpec.startXPx + randomSpec.dirX * randomSpec.dist * p - widthPx / 2f
                translationY = (anchorWindowY - rootWindowY) + randomSpec.dirY * randomSpec.dist * p
                this.alpha = 1f - p
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        BasicText(
            text = "${bubble.label}+${bubble.count}",
            style = TextStyle(
                color = Color.White,
                fontSize = with(density) { animatedSize.toSp() },
                fontWeight = FontWeight.Black,
                shadow = textShadow,
            ),
        )
    }
}

/** 气泡随机参数(remember 持有,动画全程不变)。 */
private class BubbleSpec(
    val endSize: Float,
    val startScale: Float,
    val startXPx: Float,
    val dirX: Float,
    val dirY: Float,
    val dist: Float,
)
