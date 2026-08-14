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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow


import com.mylauncher.ui.rememberInnerDisplayUnfolded

/** 系统返回手势事件(边缘滑入触发):主屏收集后播功德彩蛋。 */
object LauncherEvents {
    val backGesture = MutableSharedFlow<Unit>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** 返回手势刚开始(手指刚滑入):立即敲木鱼,跟手。 */
    val backStarted = MutableSharedFlow<Unit>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

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
                    android.util.Log.w("MyLauncher", "back pressed")
                    // 主屏是根界面:返回手势不退出应用,只作为功德彩蛋的触发事件
                    LauncherEvents.backGesture.tryEmit(Unit)
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
