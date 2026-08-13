package com.mylauncher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

private fun Context.findActivity(): Activity? {
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
