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
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.7f)) {
            Text(text = pref.title(), style = MaterialTheme.typography.bodyLarge)
            pref.summary?.invoke()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
