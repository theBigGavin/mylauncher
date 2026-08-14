package com.mylauncher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.math.max

/**
 * 自定义壁纸:用户从相册选图 -> 按屏幕长边降采样存私有目录 -> 设为系统壁纸。
 * 主屏/二级页面共用同一份内存缓存;version 自增触发 UI 重载。
 */
object CustomWallpaper {

    private const val FILE_NAME = "wallpaper_custom.jpg"

    /** 图片变更版本号,Compose 侧 collect 后自动重载。 */
    val version = MutableStateFlow(0)

    private var cached: Bitmap? = null

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * 从相册 uri 导入:降采样 -> 存私有目录 -> 设为系统壁纸。
     * 需在 IO 线程调用;全程失败返回 false(调用方提示/保持原模式)。
     */
    fun setFromUri(context: Context, uri: Uri): Boolean {
        val metrics = context.resources.displayMetrics
        val longSide = max(metrics.widthPixels, metrics.heightPixels)
        val bmp = decodeScaled(context, uri, longSide) ?: return false

        runCatching {
            file(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }.onFailure { return false }

        // 设为系统壁纸(SET_WALLPAPER 普通权限);ROM 拒绝时视为失败
        runCatching { WallpaperManager.getInstance(context).setBitmap(bmp) }
            .onFailure { return false }

        cached = bmp
        version.value++
        return true
    }

    /** 读取已保存的自定义壁纸(内存缓存一份);文件丢失返回 null(调用方回退内置壁纸)。 */
    fun load(context: Context): Bitmap? {
        cached?.let { return it }
        val f = file(context)
        if (!f.exists()) return null
        val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() ?: return null
        cached = bmp
        return bmp
    }

    /** 两遍解码:先量尺寸,再按长边 <= longSide 采样加载。 */
    private fun decodeScaled(context: Context, uri: Uri, longSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }.onFailure { return null }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
    }
}
