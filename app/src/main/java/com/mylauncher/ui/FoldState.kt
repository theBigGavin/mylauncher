package com.mylauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

/**
 * 折叠屏"内屏展开"判定。
 *
 * 语义(以 WindowManager 上报为准):
 * - 折叠机**打开、应用跑在内屏**时,当前窗口布局会携带一个 state == FLAT 的
 *   FoldingFeature(铰链区域存在但已摊平);
 * - 折叠后应用跑在外屏/小屏时,当前显示区域通常**没有** FoldingFeature(或为
 *   HALF_OPENED 的半折/帐篷姿态);
 * - 非折叠设备永远没有 FoldingFeature → 返回 false,行为与之前完全一致。
 */
internal fun isInnerDisplayUnfolded(info: WindowLayoutInfo?): Boolean {
    val folding = info?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()
        ?: return false
    return folding.state == FoldingFeature.State.FLAT
}

/** 收集 WindowInfoTracker 的窗口布局信息,输出"内屏是否展开"。 */
@Composable
fun rememberInnerDisplayUnfolded(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val layoutInfo by produceState<WindowLayoutInfo?>(null, activity) {
        if (activity != null) {
            WindowInfoTracker.getOrCreate(context)
                .windowLayoutInfo(activity)
                .collect { value = it }
        }
    }
    return isInnerDisplayUnfolded(layoutInfo)
}
