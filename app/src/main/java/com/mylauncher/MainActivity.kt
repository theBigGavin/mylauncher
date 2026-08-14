package com.mylauncher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import com.mylauncher.ui.HomeScreen
import com.mylauncher.ui.rememberInnerDisplayUnfolded

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // 主屏是根界面:系统返回手势(边缘滑入)不应退出应用,避免闪出默认背景
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // 无操作:保持主屏(浮层由 Compose BackHandler 自行处理)
                }
            }
        )
        setContent {
            // MaterialTheme 提供 Material 水波纹(dark 配色 → 白色涟漪,适配深色壁纸)
            MaterialTheme(colors = darkColors()) {
                HomeScreen(innerDisplayUnfolded = rememberInnerDisplayUnfolded())
            }
        }
    }
}
