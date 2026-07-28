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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lalilu.common.settings.SwitchPreference


/**
 * Switch 偏好项的 Material3 行实现。
 *
 * ## 视觉约定（与项目内其他列表行统一）
 *
 * - `Row.padding(horizontal=16dp, vertical=12dp)`，无 Card 外壳
 * - 标题 `bodyLarge` + `onBackground`
 * - 副标题 `bodySmall` 12sp + `onBackground.copy(0.6f)`
 * - 行内 `Arrangement.spacedBy(12.dp)` 文本 + Switch 控件
 *
 * ## 交互
 *
 * - 点击整行 = 切换 [SwitchPreference.value]（点击区域更大，移动端友好）
 * - 点击内部 Material3 [Switch] 同样会触发切换
 * - [SwitchPreference.enabled] 返回 `false` 时整行被 `disabled()` 语义标记
 *
 * 测试 tag：`preference_switch_<key>`
 */
@Composable
fun SwitchPreferenceRow(
    pref: SwitchPreference,
    modifier: Modifier = Modifier
) {
    val isEnabled = pref.enabled()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preference_switch_${pref.key}")
            .clickable(enabled = isEnabled) {
                pref.onValueChange(!pref.value)
            }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                if (!isEnabled) disabled()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        }
        Switch(
            checked = pref.value,
            onCheckedChange = { newValue ->
                if (isEnabled) pref.onValueChange(newValue)
            },
            enabled = isEnabled
        )
    }
}
