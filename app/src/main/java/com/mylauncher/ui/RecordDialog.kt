package com.mylauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mylauncher.R

/**
 * 破纪录弹窗(20b):敲出历史新纪录时主页弹出。
 * 文案:基础「恭喜你刷新了记录!」,percentile 拉取成功时接「超过了全球 x% 的 MyLauncher 用户!」。
 * 两操作:分享记录到全球榜单(跳设置页上传)/ 生成分享图邀请朋友来挑战(跳 20a 分享图)。
 * 可关闭(按钮 / 点外部),会话内只弹一次(防打扰在调用方控制)。
 */
@Composable
fun RecordDialog(
    /** 最快手速展示,如 "23.3 次/秒"(已带单位,按当前 Locale 格式化)。 */
    rateText: String,
    /** GET /percentile 返回的比例(0-100,已格式化字符串);null = 后端未上线/请求失败,回退基础文案。 */
    percentileText: String?,
    onShareBoard: () -> Unit,
    onShareImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(320.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp))
                .background(Color(0xFFFAFAFC), RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) {
            // 主文案:percentile 可用时用完整句,否则基础句(二选一,避免重复)
            BasicText(
                text = if (percentileText != null) {
                    androidx.compose.ui.res.stringResource(
                        R.string.record_congrats,
                        percentileText,
                    )
                } else {
                    androidx.compose.ui.res.stringResource(R.string.record_title)
                },
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = Color(0xFF1E1E22),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.height(14.dp))
            BasicText(
                text = androidx.compose.ui.res.stringResource(R.string.record_rate_line, rateText),
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = Color(0xFF1E1E22),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.height(24.dp))
            // 主操作:分享记录到全球榜单(跳设置页排行榜小节上传)
            RecordActionButton(
                text = androidx.compose.ui.res.stringResource(R.string.record_share_board),
                primary = true,
                onClick = onShareBoard,
            )
            Spacer(Modifier.height(10.dp))
            // 次操作:生成分享图(复用 20a ShareImageActivity)
            RecordActionButton(
                text = androidx.compose.ui.res.stringResource(R.string.record_share_image),
                primary = false,
                onClick = onShareImage,
            )
            Spacer(Modifier.height(6.dp))
            // 关闭:点外部或此按钮均可
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                BasicText(
                    text = androidx.compose.ui.res.stringResource(R.string.record_close),
                    style = TextStyle(
                        color = Color(0xFF1E1E22).copy(alpha = 0.45f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RecordActionButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (primary) Color(0xFF1E1E22) else Color(0xFF1E1E22).copy(alpha = 0.07f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = if (primary) Color.White else Color(0xFF1E1E22),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}
