package com.mylauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** 色值与 mockup.html 的 repeating-linear-gradient / render_mockup.py 完全一致。
 * 分享图(ui/share)复用同一色板 —— 改色必须三处同步:BANDS 与 mockup.html / render_mockup.py。 */
internal val BANDS = listOf(
    0.200f to Color(0xFF120E2C),
    0.035f to Color(0xFFFF3D00),
    0.130f to Color(0xFF1A123A),
    0.016f to Color(0xFF00E5C8),
    0.240f to Color(0xFF0E0B24),
    0.055f to Color(0xFFFF008C),
    0.100f to Color(0xFF161032),
    0.012f to Color(0xFFCCFF00),
    0.180f to Color(0xFF100C28),
    0.028f to Color(0xFFFF5E3A),
)

internal val LINE_GREEN = Color(0xFFCCFF00)
internal val LINE_TEAL = Color(0xFF00E5C8)
internal val RING_YELLOW = Color(0xFFFFD600)

/**
 * 生成式锐利撞色壁纸(无模糊):
 * 62° 硬边斜带 + 反向 -24° 细锐线(荧光绿/白/青)+ 右上硬边黄色圆环。
 */
@Composable
fun Wallpaper(modifier: Modifier = Modifier, showMiddleLine: Boolean = true) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // ---- 62° 撞色斜带(硬边色块,逐带裁剪为多边形)----
        val theta = Math.toRadians(62.0)
        val ct = cos(theta).toFloat()
        val st = sin(theta).toFloat()
        val tmin = 0f
        val tmax = w * ct + h * st
        val period = BANDS.sumOf { it.first.toDouble() }.toFloat() * h

        var t = floor(tmin / period) * period
        while (t < tmax) {
            for ((frac, color) in BANDS) {
                val a = t
                val b = t + frac * h
                if (b > tmin && a < tmax) {
                    stripPath(a, b, ct, st, w, h)?.let { drawPath(it, color) }
                }
                t = b
            }
        }

        // ---- -24° 反方向细锐线 ----
        val slope = tan(Math.toRadians(-24.0)).toFloat()
        fun slantLine(fracY: Float, color: Color, widthPx: Float) {
            val y0 = fracY * h
            drawLine(
                color = color,
                start = Offset(-40f, y0),
                end = Offset(w + 40f, y0 + (w + 80f) * slope),
                strokeWidth = widthPx,
            )
        }
        slantLine(0.16f, LINE_GREEN, maxOf(2f, h * 5f / 1760f))
        if (showMiddleLine) slantLine(0.19f, Color.White, maxOf(1f, h * 2f / 1760f))
        slantLine(0.78f, LINE_TEAL, maxOf(2f, h * 4f / 1760f))

        // ---- 右上硬边圆环(部分出画)----
        drawCircle(
            color = RING_YELLOW,
            radius = 0.30f * minOf(w, h),
            center = Offset(0.86f * w, 0.10f * h),
            style = Stroke(width = maxOf(6f, h / 200f)),
        )
    }
}

/** 无限长条带 a <= x*ct + y*st < b 与屏幕矩形的交集多边形(凸包顶点 <= 6)。 */
private fun stripPath(a: Float, b: Float, ct: Float, st: Float, w: Float, h: Float): Path? {
    val pts = ArrayList<Offset>(8)

    fun add(x: Float, y: Float) {
        if (pts.none { abs(it.x - x) < 0.5f && abs(it.y - y) < 0.5f }) pts.add(Offset(x, y))
    }

    // 矩形四角落在条带内的
    for ((x, y) in listOf(0f to 0f, w to 0f, w to h, 0f to h)) {
        val t = x * ct + y * st
        if (t in a..b) add(x, y)
    }
    // 条带两条边界线与矩形边的交点
    for (t in listOf(a, b)) {
        if (abs(st) > 1e-6f) {
            val y0 = t / st
            if (y0 in 0f..h) add(0f, y0)
            val y1 = (t - w * ct) / st
            if (y1 in 0f..h) add(w, y1)
        }
        if (abs(ct) > 1e-6f) {
            val x0 = t / ct
            if (x0 in 0f..w) add(x0, 0f)
            val x1 = (t - h * st) / ct
            if (x1 in 0f..w) add(x1, h)
        }
    }
    if (pts.size < 3) return null

    val cx = pts.map { it.x }.average().toFloat()
    val cy = pts.map { it.y }.average().toFloat()
    pts.sortBy { atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }

    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    path.close()
    return path
}
