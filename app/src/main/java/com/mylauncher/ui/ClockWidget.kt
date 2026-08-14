package com.mylauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 一个功德气泡:从小放大扩散、白→透明消失,位置/字号随机(不影响布局)。 */
data class MeritBubbleData(val id: Int, val count: Int)

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

    // 记录时钟在窗口中的 Y,供功德气泡向屏幕顶端上升
    var windowY by remember { mutableStateOf(0f) }
    BoxWithConstraints(
        modifier.onGloballyPositioned { windowY = it.positionInWindow().y },
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = time,
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
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = dateSize.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = if (landscape) 3.sp else 4.sp,
                    shadow = textShadow,
                ),
            )
        }
        // 功德气泡层:绝对定位 + graphicsLayer 动画,不影响布局、不闪烁
        if (meritBubbles.isNotEmpty()) {
            val clockPx = with(LocalDensity.current) { timeSize.sp.toPx() }
            meritBubbles.forEach { bubble ->
                MeritBubble(
                    bubble = bubble,
                    clockFontSizePx = clockPx,
                    areaW = maxWidth,
                    // 从时钟文字顶部开始冒泡,向屏幕顶端扩散
                    clockTopWindowY = windowY,
                    onDone = { onMeritBubbleDone(bubble.id) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

/** 单个功德气泡:从时钟文字顶部冒泡,沿选定方向(向上斜线)扩散 + 放大 + 白→透明消失。 */
@Composable
private fun MeritBubble(
    bubble: MeritBubbleData,
    clockFontSizePx: Float,
    areaW: Dp,
    clockTopWindowY: Float,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // 字号固定为最终大小(时钟字号的 70%~90% 随机,定下后不再变);
    // 动画只用 graphicsLayer 缩放整体放大,不修改字体大小
    val endSize = clockFontSizePx * (0.70f + Random.nextFloat() * 0.20f)
    val startScale = 0.30f + Random.nextFloat() * 0.30f // 初始缩放随机(0.3~0.6)
    // 起始水平位置:时钟中间 1/3 范围内随机(垂直在时钟文字顶部)
    val startXPx = (Random.nextFloat() * 2f - 1f) * with(density) { areaW.toPx() } / 6f
    // 扩散方向:向上偏左/偏右的斜线(±25° 随机,选定后不变,不左右乱晃)
    val angleDeg = (Random.nextFloat() * 2f - 1f) * 25f
    val rad = Math.toRadians(angleDeg.toDouble())
    val dirX = sin(rad).toFloat()
    val dirY = -cos(rad).toFloat()
    // 扩散距离:时钟顶部到屏幕顶端的距离(随机比例)
    val dist = clockTopWindowY * (0.8f + Random.nextFloat() * 0.4f)
    val progress = remember { Animatable(0f) }
    // 贝塞尔曲线控制速度:快起慢收(冒泡感),运动平滑无抖动
    val bubbleEasing = CubicBezierEasing(0.0f, 0.45f, 0.25f, 1.0f)
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1300, easing = bubbleEasing))
        onDone()
    }
    Box(
        modifier
            .offset(x = with(density) { startXPx.toDp() })
            .graphicsLayer {
                // 关键:progress 只在 layer 块内读取,不触发重组(否则文字每帧重排会闪烁)
                val p = progress.value
                translationX = dirX * dist * p
                translationY = dirY * dist * p
                val sc = startScale + (1f - startScale) * p
                scaleX = sc
                scaleY = sc
                this.alpha = 1f - p
                // 缩放原点在文字顶部:气泡从时间文字顶部出现,整体向下放大
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        BasicText(
            text = "功德+${bubble.count}",
            style = TextStyle(
                color = Color.White,
                fontSize = with(density) { endSize.toSp() },
                fontWeight = FontWeight.Black,
                shadow = textShadow,
            ),
        )
    }
}
