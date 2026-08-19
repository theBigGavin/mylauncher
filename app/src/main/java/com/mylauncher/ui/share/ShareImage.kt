package com.mylauncher.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color as ComposeColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mylauncher.ui.BANDS
import com.mylauncher.ui.LINE_GREEN
import com.mylauncher.ui.LINE_TEAL
import com.mylauncher.ui.RING_YELLOW
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * 手速分享图(里程牌 2,A 批,纯客户端)。
 *
 * 画布尺寸 1080×1440(3:4 竖版分享卡,微信/系统分享通用)。Canvas 分层自绘:
 *   1. 背景层 —— 复用壁纸生成算法(BANDS 斜带 + 细线 + 圆环,色板与 Wallpaper.kt 同一常量,
 *      改色三处同步铁律依旧:BANDS ↔ mockup.html ↔ render_mockup.py)
 *   2. 压暗层 —— 半透明黑色遮罩,保证白字在撞色背景上可读
 *   3. 内容层 —— 品牌 / 手速大数字 / 昵称 / 每日最高功德 / CTA 文案 / 下载二维码
 *
 * 文案全部由调用方按当前 Locale 解析后传入(见 ShareImageData),渲染层不触碰资源。
 */

/** 分享图下载二维码指向的地址:全球榜单页(带 UTM 引流跟踪)。 */
const val LEADERBOARD_URL = "https://www.hermes.cc.cd/leaderboard/?utm_source=mylauncher&utm_medium=share_image&utm_campaign=leaderboard"

/** 分享图渲染输入:文案已按当前 Locale 由调用方解析好。 */
data class ShareImageData(
    /** 最快手速数字文本,如 "23.3"。 */
    val rateText: String,
    /** 单位文本,如 "次/秒"。 */
    val rateUnit: String,
    /** 昵称。 */
    val nickname: String,
    /** 每日最高功德文本,如 "功德 233"。 */
    val meritText: String,
    /** CTA 文案:"快来下载 MyLauncher 挑战我"。 */
    val tagline: String,
    /** 二维码下方小字:"扫码看全球榜"。 */
    val downloadHint: String,
    /** 二维码内容(全球榜单页地址,带 UTM)。 */
    val qrContent: String = LEADERBOARD_URL,
)

private const val CARD_W = 1080
private const val CARD_H = 1440
private const val WHITE = 0xFFFFFFFF.toInt()

/** Compose Color(ULong 值类)→ Android ARGB Int,避免依赖 toArgb() 的版本差异。 */
private fun ComposeColor.toArgbInt(): Int {
    val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
    val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/** 生成手速分享图 Bitmap(含二维码)。耗时 ms 级,可直接在 IO 线程调用。 */
fun renderShareImage(data: ShareImageData): Bitmap {
    val bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // 1. 背景:壁纸同款撞色斜带 + 细线 + 圆环
    drawWallpaperBackdrop(canvas)
    // 2. 压暗层:保证前景白字可读
    canvas.drawColor(0x59000000)
    // 3. 内容层
    drawContent(canvas, data)
    return bmp
}

/** 用 ZXing core 生成二维码 Bitmap(白底黑点,供分享图白卡直接覆盖)。 */
fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val row = y * sizePx
        for (x in 0 until sizePx) {
            pixels[row + x] = if (matrix[x, y]) 0xFF000000.toInt() else WHITE
        }
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

// ---------- 背景层:与 Wallpaper.kt 同款几何(62° 斜带 + -24° 细线 + 右上圆环) ----------

private fun drawWallpaperBackdrop(canvas: Canvas) {
    val w = CARD_W.toFloat()
    val h = CARD_H.toFloat()
    val theta = Math.toRadians(62.0)
    val ct = cos(theta).toFloat()
    val st = sin(theta).toFloat()
    val tmin = 0f
    val tmax = w * ct + h * st
    val period = BANDS.sumOf { it.first.toDouble() }.toFloat() * h

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    var t = floor(tmin / period) * period
    while (t < tmax) {
        for ((frac, color) in BANDS) {
            val a = t
            val b = t + frac * h
            if (b > tmin && a < tmax) {
                stripPath(a, b, ct, st, w, h)?.let { path ->
                    fill.color = color.toArgbInt()
                    canvas.drawPath(path, fill)
                }
            }
            t = b
        }
    }

    // -24° 反方向细锐线
    val slope = tan(Math.toRadians(-24.0)).toFloat()
    fun slantLine(fracY: Float, colorInt: Int, widthPx: Float) {
        val y0 = fracY * h
        val p = Paint().apply {
            color = colorInt
            strokeWidth = widthPx
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(-40f, y0, w + 40f, y0 + (w + 80f) * slope, p)
    }
    slantLine(0.16f, LINE_GREEN.toArgbInt(), maxOf(2f, h * 5f / 1760f))
    slantLine(0.19f, WHITE, maxOf(1f, h * 2f / 1760f))
    slantLine(0.78f, LINE_TEAL.toArgbInt(), maxOf(2f, h * 4f / 1760f))

    // 右上硬边圆环(部分出画)
    canvas.drawCircle(
        0.86f * w,
        0.10f * h,
        0.30f * minOf(w, h),
        Paint().apply {
            color = RING_YELLOW.toArgbInt()
            style = Paint.Style.STROKE
            strokeWidth = maxOf(6f, h / 200f)
            isAntiAlias = true
        },
    )
}

/** 无限长条带 a <= x*ct + y*st < b 与矩形区域的交集多边形(凸包顶点 <= 6)。 */
private fun stripPath(a: Float, b: Float, ct: Float, st: Float, w: Float, h: Float): Path? {
    val pts = ArrayList<android.graphics.PointF>(8)

    fun add(x: Float, y: Float) {
        if (pts.none { abs(it.x - x) < 0.5f && abs(it.y - y) < 0.5f }) pts.add(android.graphics.PointF(x, y))
    }

    for ((x, y) in listOf(0f to 0f, w to 0f, w to h, 0f to h)) {
        val t = x * ct + y * st
        if (t in a..b) add(x, y)
    }
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

// ---------- 内容层 ----------

private fun drawContent(canvas: Canvas, data: ShareImageData) {
    val cx = CARD_W / 2f

    // 品牌
    val brand = textPaint(68f, WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 0.14f)
    brand.textAlign = Paint.Align.CENTER
    canvas.drawText("MyLauncher", cx, 185f, brand)

    // 品牌下强调条(青色,与壁纸细线同色)
    val bar = Paint().apply { color = LINE_TEAL.toArgbInt() }
    canvas.drawRect(cx - 70f, 240f, cx + 70f, 246f, bar)

    // 最快手速大数字(sans-serif-light 大号,与时钟字体气质一致)
    val num = textPaint(330f, WHITE, Typeface.create("sans-serif-light", Typeface.NORMAL), 0f, 10f)
    val numW = num.measureText(data.rateText)
    val numX = (CARD_W - numW) / 2f
    canvas.drawText(data.rateText, numX, 585f, num)

    // 单位(数字右侧,弱化)
    val unit = textPaint(78f, 0xB3FFFFFF.toInt(), Typeface.DEFAULT, 0f)
    canvas.drawText(data.rateUnit, numX + numW + 28f, 561f, unit)

    // 昵称
    val nick = textPaint(52f, 0xE6FFFFFF.toInt(), Typeface.DEFAULT, 0f)
    nick.textAlign = Paint.Align.CENTER
    canvas.drawText(data.nickname, cx, 715f, nick)

    // 每日最高功德
    val merit = textPaint(46f, 0xB8FFFFFF.toInt(), Typeface.DEFAULT, 0f)
    merit.textAlign = Paint.Align.CENTER
    canvas.drawText(data.meritText, cx, 815f, merit)

    // CTA 文案(荧光绿,品牌强调色)
    val tag = textPaint(44f, LINE_GREEN.toArgbInt(), Typeface.create(Typeface.DEFAULT, Typeface.BOLD), 0f)
    tag.textAlign = Paint.Align.CENTER
    canvas.drawText(data.tagline, cx, 960f, tag)

    // 二维码:白底圆角卡 + 二维码 + 下方小字
    val qrSize = 310
    val card = RectF(cx - 175f, 1005f, cx + 175f, 1355f)
    canvas.drawRoundRect(card, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
    canvas.drawBitmap(generateQrBitmap(data.qrContent, qrSize), card.left + 20f, card.top + 20f, null)

    val hint = textPaint(32f, 0xA6FFFFFF.toInt(), Typeface.DEFAULT, 0f)
    hint.textAlign = Paint.Align.CENTER
    canvas.drawText(data.downloadHint, cx, card.bottom + 52f, hint)
}

private fun textPaint(
    sizePx: Float,
    color: Int,
    typeface: Typeface,
    letterSpacing: Float,
    shadowRadius: Float = 6f,
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = sizePx
    this.color = color
    this.typeface = typeface
    if (letterSpacing != 0f) this.letterSpacing = letterSpacing
    setShadowLayer(shadowRadius, 0f, 3f, 0x66000000)
}
