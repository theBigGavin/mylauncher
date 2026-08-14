package com.mylauncher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mylauncher.data.CustomWallpaper
import com.mylauncher.data.HomeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 跟随系统壁纸的实现方式:FLAG_SHOW_WALLPAPER。
 *
 * 说明:API 33+ 上 WallpaperManager.getDrawable() 需要 READ_WALLPAPER_INTERNAL(签名级)
 * 或 READ_EXTERNAL_STORAGE(targetSdk 33+ 不再授予),普通应用无法再把系统壁纸读成 Bitmap。
 * 标准做法是给我们的窗口设置 FLAG_SHOW_WALLPAPER,让系统壁纸引擎直接渲染在窗口之下
 * (同时支持动态壁纸),本层只盖 20% 黑色暗纱保证白色文字可读。
 * 系统壁纸变化/动态壁纸帧由系统自行渲染,无需刷新逻辑。
 */
@Composable
fun ApplyShowWallpaperFlag(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (window != null) {
            val attrs = window.attributes
            attrs.flags = if (enabled) {
                attrs.flags or WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
            } else {
                attrs.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER.inv()
            }
            window.attributes = attrs
        }
        onDispose {
            if (enabled && window != null) {
                val attrs = window.attributes
                attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER.inv()
                window.attributes = attrs
            }
        }
    }
}

internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** 系统壁纸模式的前景层:20% 黑色暗纱(壁纸本体由系统渲染在本窗口之下)。 */
@Composable
fun SystemWallpaperScrim(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = 0.2f)))
}

/**
 * 页面可见期间开启窗口背景模糊(毛玻璃),销毁时还原。
 * 双路径同开:
 * - FLAG_BLUR_BEHIND + blurBehindRadius(模糊窗口"后面"的内容;AOSP 标准,但 ColorOS 不执行)
 * - backgroundBlurRadius(窗口自身背景区域模糊;系统对话框/系统桌面用的这条,ColorOS 执行)
 * API 31+ 有效,以下无模糊能力(调用方用更深的 scrim 补偿)。
 */
@Composable
fun ApplyBlurBehind(enabled: Boolean, radiusPx: Int = 25) {
    if (Build.VERSION.SDK_INT < 31) return
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (window != null && enabled) {
            val attrs = window.attributes
            attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            attrs.blurBehindRadius = radiusPx
            window.attributes = attrs
            // 窗口自身背景模糊(公开 SDK API;系统对话框/桌面走这条,ColorOS 执行)
            window.setBackgroundBlurRadius(radiusPx)
        }
        onDispose {
            if (window != null) {
                val attrs = window.attributes
                attrs.flags = attrs.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                attrs.blurBehindRadius = 0
                window.attributes = attrs
                window.setBackgroundBlurRadius(0)
            }
        }
    }
}

/**
 * 二级页面(选择器 / 抽屉 / 设置页)的毛玻璃背景:
 * - custom(自定义图片):自存 bitmap + Modifier.blur(28dp, API 31+) 真模糊 + 45% 黑纱
 *   (图片放大 8% 再模糊,避免边缘虚化露底;API<31 降级为仅黑纱;文件丢失回退内置)
 * - system(跟随系统):页面不铺底色,系统壁纸经 FLAG_SHOW_WALLPAPER 透入,
 *   窗口模糊(blurBehind + backgroundBlurRadius 双路径,见 ApplyBlurBehind)+ 50% 黑纱
 * - builtin:内置几何壁纸 + 50% 黑纱
 *
 * 关于窗口级模糊的可靠性:ColorOS(OnePlus Open,Android 16)实测 blurBehind 与
 * backgroundBlurRadius 都被挂到全透明 Task Dim Layer,内容层始终为 0(不执行);
 * 此时仅靠黑纱保证可读。custom 模式的 Modifier.blur 模糊的是我们自己窗口里的 bitmap,
 * 不依赖 ROM 的窗口模糊支持。
 */
@Composable
fun GlassPageBackground(wallpaperMode: String, modifier: Modifier = Modifier) {
    when (wallpaperMode) {
        HomeStore.WALLPAPER_CUSTOM -> {
            val bitmap = rememberCustomWallpaper(enabled = true)
            if (bitmap != null) {
                Box(modifier.fillMaxSize().clipToBounds()) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.08f)
                            .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            } else {
                // 自定义图片丢失:回退内置几何壁纸
                Wallpaper(modifier.fillMaxSize(), showMiddleLine = false)
                Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            }
        }
        else -> {
            val systemWallpaper = wallpaperMode == HomeStore.WALLPAPER_SYSTEM
            ApplyBlurBehind(enabled = systemWallpaper && Build.VERSION.SDK_INT >= 31)
            if (!systemWallpaper) {
                Wallpaper(modifier.fillMaxSize(), showMiddleLine = false)
            }
            Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }
    }
}

/** 加载自定义壁纸(IO 线程,内存缓存一份);enabled=false 或文件缺失时返回 null。 */
@Composable
fun rememberCustomWallpaper(enabled: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val version by CustomWallpaper.version.collectAsState()
    return produceState<ImageBitmap?>(null, enabled, version) {
        if (!enabled) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            CustomWallpaper.load(context)?.asImageBitmap()
        }
    }.value
}
