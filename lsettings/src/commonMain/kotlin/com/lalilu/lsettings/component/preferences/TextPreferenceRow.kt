/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * ## 视觉约定（与项目内其他列表行统一）
 *
 * - `Column.padding(horizontal=16dp, vertical=12dp)`，无 Card 外壳
 * - 标题 `bodyLarge` + `onBackground`
 * - 副标题 `bodySmall` 12sp + `onBackground.copy(0.6f)`
 * - 当前值 `bodyMedium` + `primary`，空值时显示"未设置"占位
 *
 * ## 行为
 *
 * - 点击行弹 [TextInputDialog] 输入新值
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
        Text(
            text = pref.title(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        pref.summary?.invoke()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
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
