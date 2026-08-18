package com.mylauncher.ui.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mylauncher.R
import com.mylauncher.data.HomeStore
import com.mylauncher.ui.PORTRAIT_ROW_MARGIN
import com.mylauncher.ui.SubPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 手速分享图入口(A 批,入口预留):
 * 读 HomeStore 战绩(最快手速 / 每日最高功德 / 昵称)→ Canvas 生成 → 自动保存 MediaStore 并 Toast 路径
 * → 预览 + 系统分享(ACTION_SEND)。
 *
 * 20b 的「手速排行榜」小节与破纪录弹窗通过 [launch] 携带昵称跳转,复用本页。
 */
class ShareImageActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NICKNAME = "nickname"

        /** 打开分享图页(可带昵称;不带则回退 HomeStore 昵称/默认名)。 */
        fun launch(context: Context, nickname: String? = null) {
            context.startActivity(
                Intent(context, ShareImageActivity::class.java)
                    .putExtra(EXTRA_NICKNAME, nickname.orEmpty()),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colors = darkColors()) {
                ShareImageScreen(onDismiss = { finish() })
            }
        }
    }
}

private sealed interface ShareOutcome {
    data class Ok(val bmp: Bitmap, val uri: Uri, val path: String) : ShareOutcome
    data object NoRecord : ShareOutcome
    data object Failed : ShareOutcome
}

@Composable
private fun ShareImageScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var lastBmp by remember { mutableStateOf<Bitmap?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var savedPath by remember { mutableStateOf<String?>(null) }

    // 生成 + 自动保存(只跑一次;文案按当前 Locale 取资源)
    LaunchedEffect(Unit) {
        val nicknameExtra = (context as? Activity)
            ?.intent?.getStringExtra(ShareImageActivity.EXTRA_NICKNAME)
            ?.takeIf { it.isNotBlank() }
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val store = HomeStore(context.applicationContext)
                val home = store.data.first()
                val gapMs = home.fastestKnockGapMs
                if (gapMs <= 0) return@runCatching ShareOutcome.NoRecord
                val nickname = nicknameExtra
                    ?: home.leaderboardNickname.ifBlank { context.getString(R.string.share_default_nickname) }
                val data = ShareImageData(
                    rateText = String.format(Locale.US, "%.1f", 1000f / gapMs),
                    rateUnit = context.getString(R.string.share_rate_unit),
                    nickname = nickname,
                    meritText = "${home.meritLabel} ${home.meritPeak}",
                    tagline = context.getString(R.string.share_tagline),
                    downloadHint = context.getString(R.string.share_download_hint),
                )
                val bmp = renderShareImage(data)
                val uri = ShareImageSaver.save(context, bmp)
                ShareOutcome.Ok(bmp, uri, ShareImageSaver.displayPath(context, uri))
            }.getOrElse { ShareOutcome.Failed }
        }
        when (outcome) {
            is ShareOutcome.NoRecord -> {
                Toast.makeText(context, context.getString(R.string.share_no_record), Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            is ShareOutcome.Failed -> {
                Toast.makeText(context, context.getString(R.string.share_generate_failed), Toast.LENGTH_SHORT).show()
                failed = true
            }
            is ShareOutcome.Ok -> {
                preview = outcome.bmp.asImageBitmap()
                lastBmp = outcome.bmp
                lastUri = outcome.uri
                savedPath = outcome.path
                Toast.makeText(
                    context,
                    context.getString(R.string.share_saved_toast, outcome.path),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        busy = false
    }

    Box(Modifier.fillMaxSize().background(ComposeColor(0xFF0E0B24))) {
        SubPage(
            title = context.getString(R.string.share_title),
            subtitle = context.getString(R.string.share_subtitle),
            onDismiss = onDismiss,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = PORTRAIT_ROW_MARGIN, end = PORTRAIT_ROW_MARGIN, bottom = 48.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                when {
                    busy -> {
                        Spacer(Modifier.height(200.dp))
                        BasicText(
                            text = "…",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            style = TextStyle(color = ComposeColor.White.copy(alpha = 0.7f), fontSize = 32.sp),
                        )
                    }
                    failed -> {
                        Spacer(Modifier.height(200.dp))
                        BasicText(
                            text = context.getString(R.string.share_generate_failed),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            style = TextStyle(color = ComposeColor.White.copy(alpha = 0.7f), fontSize = 18.sp),
                        )
                    }
                    else -> {
                        preview?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 4f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(
                                        1.dp,
                                        ComposeColor.White.copy(alpha = 0.25f),
                                        RoundedCornerShape(18.dp),
                                    ),
                            )
                        }
                        savedPath?.let { path ->
                            BasicText(
                                text = path,
                                modifier = Modifier.padding(top = 10.dp),
                                style = TextStyle(
                                    color = ComposeColor.White.copy(alpha = 0.45f),
                                    fontSize = 12.sp,
                                ),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            ShareActionButton(
                                text = context.getString(R.string.share_share),
                                strong = true,
                            ) {
                                lastUri?.let { uri ->
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(
                                                ShareImageSaver.shareIntent(context, uri),
                                                null,
                                            ),
                                        )
                                    }
                                }
                            }
                            ShareActionButton(
                                text = context.getString(R.string.share_save),
                                strong = false,
                            ) {
                                lastBmp?.let { bmp ->
                                    scope.launch {
                                        val path = withContext(Dispatchers.IO) {
                                            val uri = ShareImageSaver.save(context, bmp)
                                            ShareImageSaver.displayPath(context, uri)
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.share_saved_toast, path),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 与设置页 TextButton 同风格的文本按钮。 */
@Composable
private fun ShareActionButton(text: String, strong: Boolean, onClick: () -> Unit) {
    BasicText(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        style = TextStyle(
            color = if (strong) ComposeColor.White else ComposeColor.White.copy(alpha = 0.7f),
            fontSize = 20.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 1.sp,
        ),
    )
}
