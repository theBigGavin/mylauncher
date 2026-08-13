package com.mylauncher.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.mylauncher.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.min
import kotlin.math.pow

/**
 * 图标单色化管线:取 PackageManager 真实图标 -> Bitmap -> 亮度转 alpha、纯白色,
 * 高次幂曲线压低彩色底(与 render_mockup.py 的 to_mono_white 同算法)。
 * 内存 LruCache + 磁盘缓存(key = 组件 + versionCode + 尺寸)。
 */
object MonoIcons {

    private const val RENDER_PX = 144

    private val memoryCache = LruCache<String, Bitmap>(96)

    /** 同步尝试命中内存/磁盘缓存;未命中返回 null。 */
    fun loadCached(context: Context, entry: AppEntry, sizePx: Int): Bitmap? {
        val key = cacheKey(context, entry, sizePx) ?: return null
        memoryCache.get(key)?.let { return it }
        val file = diskFile(context, key)
        if (file.exists()) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) {
                memoryCache.put(key, bmp)
                return bmp
            }
        }
        return null
    }

    /** 完整加载(含单色化转换),调用方需在 IO 线程执行。 */
    fun load(context: Context, entry: AppEntry, sizePx: Int): Bitmap {
        loadCached(context, entry, sizePx)?.let { return it }
        val drawable = loadIconDrawable(context, entry)
        val rendered = drawableToBitmap(drawable, RENDER_PX)
        val scaled =
            if (sizePx == RENDER_PX) rendered
            else Bitmap.createScaledBitmap(rendered, sizePx, sizePx, true)
        val mono = toMonoWhite(scaled)
        cacheKey(context, entry, sizePx)?.let { key ->
            memoryCache.put(key, mono)
            runCatching {
                val f = diskFile(context, key)
                f.parentFile?.mkdirs()
                f.outputStream().use { mono.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return mono
    }

    private fun loadIconDrawable(context: Context, entry: AppEntry): Drawable {
        val pm = context.packageManager
        return runCatching { pm.getActivityIcon(ComponentName(entry.packageName, entry.activityName)) }
            .recoverCatching { pm.getApplicationIcon(entry.packageName) }
            .getOrElse { pm.defaultActivityIcon }
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }

    /**
     * 亮度 -> 形状取舍,输出**不透明**纯白:
     * lum^2.1 * 1.2 曲线压低彩色底,亮部输出纯白、暗部透明,中间留平滑过渡带抗锯齿。
     * 注意:与 render_mockup.py 的半透明版 to_mono_white 已分叉(实机不透明更耐壁纸亮线),后续需同步 mockup。
     */
    private fun toMonoWhite(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = c ushr 24 and 0xff
            val r = c ushr 16 and 0xff
            val g = c ushr 8 and 0xff
            val b = c and 0xff
            // PIL convert("L") 的 ITU-R 601-2 权重
            val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            val curved = min(1.0, lum.pow(2.1) * 1.2) * (a / 255.0)
            pixels[i] = when {
                curved >= 0.45 -> 0xFFFFFFFF.toInt() // 亮部 glyph:不透明纯白
                curved >= 0.30 -> // 过渡带:平滑抗锯齿
                    (((curved - 0.30) / 0.15 * 255).toInt() shl 24) or 0x00FFFFFF
                else -> 0x00000000 // 彩色底/暗部:透明
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun cacheKey(context: Context, entry: AppEntry, sizePx: Int): String? {
        val version = runCatching {
            context.packageManager.getPackageInfo(entry.packageName, 0).longVersionCode
        }.getOrNull() ?: return null
        return md5("${entry.component}#$version@$sizePx")
    }

    private fun diskFile(context: Context, key: String): File =
        File(File(context.cacheDir, "mono_icons"), "$key.png")

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** 按当前 dp 尺寸异步加载单色图标;先同步命中缓存,再退回 IO 线程转换。 */
@Composable
fun rememberMonoIcon(entry: AppEntry, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(24)
    // 注意:produceState 内部的 value 是无 key 的 remember,列表按位置复用组合时
    // (如拖拽排序后)旧值会残留 —— 不能在 producer 里用 value==null 短路,
    // 每次 keys 变化都必须重新按当前 entry 取图(内存/磁盘缓存保证足够快)。
    return produceState<ImageBitmap?>(null, entry.component, sizePx) {
        value = MonoIcons.loadCached(context, entry, sizePx)?.asImageBitmap()
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                MonoIcons.load(context, entry, sizePx).asImageBitmap()
            }
        }
    }.value
}
