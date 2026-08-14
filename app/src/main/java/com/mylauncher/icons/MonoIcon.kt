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

/**
 * 图标单色化管线 v3(去色提亮):整体渲染原图标(AdaptiveIcon 前景+背景合成),
 * **alpha 原样保留**(图标轮廓不变 → 视觉尺寸天然一致),RGB 去色为感知亮度、
 * 对比度微调后映射到高亮区间,输出不透明的白/浅灰圆角方块,内部图案以灰白明暗可辨
 * (iOS 浅色单色图标风),深色/照片壁纸不透。
 * 内存 LruCache + 磁盘缓存(key = 算法版本 + 组件 + versionCode + 尺寸)。
 */
object MonoIcons {

    private const val RENDER_PX = 144

    /** 算法版本:换算法时递增,避免命中旧算法缓存。 */
    private const val CACHE_VERSION = "v6-desat-bright-hc3"

    private val memoryCache = LruCache<String, Bitmap>(96)
    private val colorCache = LruCache<String, Bitmap>(64)

    /** 内存缓存键:无 versionCode,纯字符串拼接,零 IPC,可在主线程组合期调用。 */
    private fun memKey(entry: AppEntry, sizePx: Int) = "${entry.component}@$sizePx"

    /** 仅查内存缓存(零 IPC/IO,主线程安全)。 */
    fun memoryOnly(entry: AppEntry, sizePx: Int): Bitmap? = memoryCache.get(memKey(entry, sizePx))

    /** 原彩图标仅查内存缓存(零 IPC/IO,主线程安全)。 */
    fun colorMemoryOnly(entry: AppEntry, sizePx: Int): Bitmap? = colorCache.get(memKey(entry, sizePx))

    /** 同步尝试命中内存/磁盘缓存(含 PackageManager IPC 与磁盘解码,仅限 IO 线程调用)。 */
    fun loadCached(context: Context, entry: AppEntry, sizePx: Int): Bitmap? {
        memoryOnly(entry, sizePx)?.let { return it }
        val key = cacheKey(context, entry, sizePx) ?: return null
        val file = diskFile(context, key)
        if (file.exists()) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) {
                memoryCache.put(memKey(entry, sizePx), bmp)
                return bmp
            }
        }
        return null
    }

    /** 原彩图标:直接取系统图标原色(带内存/磁盘缓存),调用方需在 IO 线程执行。 */
    fun loadColor(context: Context, entry: AppEntry, sizePx: Int): Bitmap {
        colorCache.get(memKey(entry, sizePx))?.let { return it }
        val key = cacheKey(context, entry, sizePx)?.let { "color|$it" }
        if (key != null) {
            val file = diskFile(context, key)
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    colorCache.put(memKey(entry, sizePx), bmp)
                    return bmp
                }
            }
        }
        val drawable = loadIconDrawable(context, entry)
        val rendered = drawableToBitmap(drawable, RENDER_PX)
        val scaled =
            if (sizePx == RENDER_PX) rendered
            else Bitmap.createScaledBitmap(rendered, sizePx, sizePx, true)
        colorCache.put(memKey(entry, sizePx), scaled)
        if (key != null) {
            runCatching {
                val f = diskFile(context, key)
                f.parentFile?.mkdirs()
                f.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return scaled
    }

    /** 完整加载(含去色提亮转换),调用方需在 IO 线程执行。 */
    fun load(context: Context, entry: AppEntry, sizePx: Int): Bitmap {
        loadCached(context, entry, sizePx)?.let { return it }
        val drawable = loadIconDrawable(context, entry)
        val rendered = drawableToBitmap(drawable, RENDER_PX)
        val scaled =
            if (sizePx == RENDER_PX) rendered
            else Bitmap.createScaledBitmap(rendered, sizePx, sizePx, true)
        val mono = desaturateBrighten(scaled)
        memoryCache.put(memKey(entry, sizePx), mono)
        cacheKey(context, entry, sizePx)?.let { key ->
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
     * 去色提亮:alpha 原样保留;RGB -> 感知亮度 lum(ITU-R 601-2),
     * 对比度增强 lum' = clamp((lum-0.5)*2.5+0.5),再映射到 [0.02, 1.0]
     * 区间 v = 0.02 + 0.98*lum',输出灰阶 (255v,255v,255v)。
     * 高反差单色(报纸印刷风):暗部近纯黑、亮部纯白;不透明度由 alpha 决定,与亮度下限无关。
     */
    private fun desaturateBrighten(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = c ushr 24 and 0xff
            if (a == 0) {
                pixels[i] = 0
                continue
            }
            val r = c ushr 16 and 0xff
            val g = c ushr 8 and 0xff
            val b = c and 0xff
            val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            val boosted = ((lum - 0.5) * 2.5 + 0.5).coerceIn(0.0, 1.0)
            val gray = ((0.02 + 0.98 * boosted) * 255).toInt()
            pixels[i] = (a shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun cacheKey(context: Context, entry: AppEntry, sizePx: Int): String? {
        val version = runCatching {
            context.packageManager.getPackageInfo(entry.packageName, 0).longVersionCode
        }.getOrNull() ?: return null
        return md5("$CACHE_VERSION|${entry.component}#$version@$sizePx")
    }

    private fun diskFile(context: Context, key: String): File =
        File(File(context.cacheDir, "mono_icons"), "$key.png")

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** 按当前 dp 尺寸加载单色图标:组合期只查纯内存缓存(零 IPC/IO),未命中走 IO 线程。 */
@Composable
fun rememberMonoIcon(entry: AppEntry, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(24)
    // 注意:produceState 内部的 value 是无 key 的 remember,列表按位置复用组合时
    // (如拖拽排序后)旧值会残留 —— 不能在 producer 里用 value==null 短路,
    // 每次 keys 变化都必须重新按当前 entry 取图。
    // 性能:initialValue 只查 LruCache(memoryOnly);loadCached(PM IPC + 磁盘解码)
    // 一律在 IO 线程 —— 此前 producer 首行在主线程同步 loadCached,是返回桌面/抽屉滚动的卡顿源。
    return produceState<ImageBitmap?>(
        MonoIcons.memoryOnly(entry, sizePx)?.asImageBitmap(),
        entry.component, sizePx,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                MonoIcons.loadCached(context, entry, sizePx)?.asImageBitmap()
                    ?: MonoIcons.load(context, entry, sizePx).asImageBitmap()
            }
        }
    }.value
}

/**
 * 按当前 dp 尺寸加载原彩图标:组合期只查内存缓存,未命中走 IO 线程取系统原图。
 */
@Composable
fun rememberColorIcon(entry: AppEntry, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(24)
    return produceState<ImageBitmap?>(
        MonoIcons.colorMemoryOnly(entry, sizePx)?.asImageBitmap(),
        entry.component, sizePx,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                MonoIcons.loadColor(context, entry, sizePx).asImageBitmap()
            }
        }
    }.value
}

/**
 * 后台预热全部应用的图标缓存(IO 线程):抽屉/选择器打开时直接命中缓存,
 * 避免首次打开时现场走 PackageManager 拉图标,拖垮主线程导致点击延迟。
 * color = true 时预热原彩图标,否则预热单色图标。
 */
suspend fun warmUpIcons(context: Context, apps: List<AppEntry>, sizePx: Int, color: Boolean = false) =
    withContext(Dispatchers.IO) {
        apps.forEach { entry ->
            if (color) {
                MonoIcons.loadColor(context, entry, sizePx)
            } else if (MonoIcons.memoryOnly(entry, sizePx) == null) {
                MonoIcons.loadCached(context, entry, sizePx)
            }
        }
    }
