/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lalilu.lsettings.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp


/**
 * 文本输入弹层。
 *
 * - [singleLine] = true  → 单行输入，[KeyboardType.Text] 键盘
 * - [singleLine] = false → 多行输入，[heightIn] 限高 240dp
 * - [hint] 作为 placeholder 显示在值为空时
 *
 * "确认"通过 [onConfirm] 回调当前文本；"取消" 触发 [onDismiss]。
 *
 * 测试 tag：
 * - 输入框：`text_input_dialog_field`
 * - 确认按钮：`text_input_dialog_confirm`
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    singleLine: Boolean = true,
    hint: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = singleLine,
                placeholder = hint?.let { { Text(text = it) } },
                keyboardOptions = if (singleLine) KeyboardOptions(keyboardType = KeyboardType.Text) else KeyboardOptions.Default,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .testTag("text_input_dialog_field")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                modifier = Modifier.testTag("text_input_dialog_confirm")
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
