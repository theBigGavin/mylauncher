package com.mylauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** 改名对话框:输入框(最多 12 字),IME 完成 / 确定 生效。 */
@Composable
fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }

    fun confirm() {
        val name = value.trim()
        if (name.isNotEmpty()) onConfirm(name) else onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(280.dp)
                .shadow(16.dp, RoundedCornerShape(16.dp))
                .background(Color(0xFFFAFAFC), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            BasicText(
                text = "修改名称",
                style = TextStyle(
                    color = Color(0xFF1E1E22),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = value,
                // 不设长度上限:长名称(如 ReVanced Manager Plus)被旧 12 字符上限锁死
                // 无法编辑(哪怕删一个字也被拒,输入框像冻结)——行显示有省略号兜底
                onValueChange = { value = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF1E1E22),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                ),
                cursorBrush = SolidColor(Color(0xFF1E1E22)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Column {
                        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) { inner() }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color(0xFF1E1E22))
                        )
                    }
                },
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogButton("取消", onDismiss)
                Spacer(Modifier.width(12.dp))
                DialogButton("确定") { confirm() }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = Color(0xFF1E1E22),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
