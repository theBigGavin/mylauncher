package com.mylauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar

/** 实时时钟:HH:mm + "星期X · M月D日"。竖屏居中超大细体;横屏左下较小。 */
@Composable
fun ClockWidget(landscape: Boolean, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            delay(1000)
        }
    }

    val time = "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
    val week = "日一二三四五六"[now.get(Calendar.DAY_OF_WEEK) - 1]
    val date = "星期$week · ${now.get(Calendar.MONTH) + 1}月${now.get(Calendar.DAY_OF_MONTH)}日"

    val config = LocalConfiguration.current
    // 对应 CSS:竖屏 min(26vw, 120px);横屏 min(15vh, 84px)
    val timeSize = if (landscape) {
        minOf(84f, config.screenHeightDp * 0.15f)
    } else {
        minOf(120f, config.screenWidthDp * 0.26f)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = time,
            style = TextStyle(
                color = Color.White,
                fontSize = timeSize.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-1).sp,
                lineHeight = timeSize.sp,
            ),
        )
        Spacer(Modifier.height(if (landscape) 6.dp else 10.dp))
        BasicText(
            text = date,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.7f),
                fontSize = if (landscape) 15.sp else 18.sp,
                fontWeight = FontWeight.Light,
            ),
        )
    }
}
