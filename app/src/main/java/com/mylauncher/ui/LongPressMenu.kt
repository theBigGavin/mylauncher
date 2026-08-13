package com.mylauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider

/** 长按弹出菜单:替换应用 / 修改名称 / 移除。白色圆角卡片,吸附在按压位置。 */
@Composable
fun LongPressMenu(
    positionInWindow: IntOffset,
    onReplace: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val provider = remember(positionInWindow) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val margin = 24
                val x = positionInWindow.x
                    .coerceIn(margin, maxOf(margin, windowSize.width - popupContentSize.width - margin))
                val y = positionInWindow.y
                    .coerceIn(margin, maxOf(margin, windowSize.height - popupContentSize.height - margin))
                return IntOffset(x, y)
            }
        }
    }

    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(180.dp)
                .shadow(16.dp, RoundedCornerShape(14.dp))
                .background(Color(0xFFFAFAFC), RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
        ) {
            MenuItem("替换应用", onReplace)
            MenuDivider()
            MenuItem("修改名称", onRename)
            MenuDivider()
            MenuItem("移除", onRemove)
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp)
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = Color(0xFF1E1E22), fontSize = 15.sp),
        )
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.Black.copy(alpha = 0.08f))
    )
}
