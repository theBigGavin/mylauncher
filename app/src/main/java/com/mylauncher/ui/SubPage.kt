package com.mylauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 子页面模板:抽屉 / 选择器 / 设置页共用的页面骨架 —— 固定头部 + 内容槽。
 * 头部:返回按钮贴屏幕左缘 16dp、标题居中 26sp、副标题居中,距顶 = 状态栏 + 24dp。
 * 三个页面的标题字号与返回按钮距顶边距由此统一,改一处三页同步。
 */
@Composable
internal fun SubPage(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // 固定头部(不随内容滚动):状态栏避让 + 统一距顶
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 20.dp)
        ) {
            Box(Modifier.fillMaxWidth()) {
                BasicText(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
                BasicText(
                    text = "‹ 返回",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp,
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = subtitle,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        content()
    }
}
