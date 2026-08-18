package com.mylauncher.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分享图保存/分享:
 *  - Android 10+(API 29+) 走 MediaStore Pictures,免权限,存入系统相册;
 *  - Android 9- 存应用外部私有目录(免权限),经 FileProvider 对外分享(不会出现在相册)。
 * 均不新增任何运行时权限。
 */
object ShareImageSaver {

    private const val DIR_NAME = "MyLauncher"

    /** 保存分享图,返回可分享的 Uri(MediaStore content uri 或 FileProvider uri)。 */
    fun save(context: Context, bmp: Bitmap): Uri {
        val displayName = "mylauncher_speed_${timestamp()}.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, bmp, displayName)
        } else {
            saveToAppDir(context, bmp, displayName)
        }
    }

    /** Toast 展示用路径文案(29+ 显示相册相对路径,以下显示绝对路径)。 */
    fun displayPath(context: Context, uri: Uri): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Environment.DIRECTORY_PICTURES + "/" + DIR_NAME + "/" + uri.lastPathSegment
        } else {
            runCatching { File(uri.path!!).absolutePath }.getOrDefault(uri.toString())
        }

    /** ACTION_SEND 分享 Intent(带 PNG,含读权限授予)。 */
    fun shareIntent(context: Context, uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun saveToMediaStore(context: Context, bmp: Bitmap, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + DIR_NAME)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return uri
    }

    private fun saveToAppDir(context: Context, bmp: Bitmap, displayName: String): Uri {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), DIR_NAME)
            .apply { mkdirs() }
        val file = File(dir, displayName)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
