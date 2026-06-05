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
 * MERCHANTABILITY or FITNESS FOR FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.lalilu.lsettings.component.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lalilu.common.settings.TextPreference
import com.lalilu.lsettings.dialog.TextInputDialog


/**
 * 文本输入偏好项的 Material3 行实现。
 *
 * - 点击行弹 [TextInputDialog] 输入新值
 * - 空值时显示"未设置"占位（让用户知道这是个可填项）
 * - 行内仅显示当前值（短截），完整编辑走对话框
 *
 * 测试 tag：`preference_text_<key>`
 */
@Composable
fun TextPreferenceRow(
    pref: TextPreference,
    modifier: Modifier = Modifier
) {
    val isEnabled = pref.enabled()
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preference_text_${pref.key}")
            .clickable(enabled = isEnabled) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = pref.title(), style = MaterialTheme.typography.bodyLarge)
        pref.summary?.invoke()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = pref.value.ifBlank { "未设置" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        TextInputDialog(
            title = pref.title(),
            initial = pref.value,
            singleLine = pref.singleLine,
            hint = pref.hint?.invoke(),
            onDismiss = { showDialog = false },
            onConfirm = { newValue ->
                showDialog = false
                pref.onValueChange(newValue)
            }
        )
    }
}
