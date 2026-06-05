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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lalilu.common.settings.DropdownPreference
import com.lalilu.lsettings.dialog.ListSelectDialog


/**
 * 单选下拉偏好项的 Material3 行实现。
 *
 * 点击行弹 [ListSelectDialog]（单选模式） → 用户选择后回调到
 * [DropdownPreference.onValueChange] → 更新 state + 写穿到 KV。
 *
 * 测试 tag：`preference_dropdown_<key>`
 */
@Composable
fun <T : Any> DropdownPreferenceRow(
    pref: DropdownPreference<T>,
    modifier: Modifier = Modifier
) {
    val isEnabled = pref.enabled()
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preference_dropdown_${pref.key}")
            .clickable(enabled = isEnabled) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = pref.title(), style = MaterialTheme.typography.bodyLarge)
            pref.summary?.invoke()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = pref.optionLabel(pref.value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        ListSelectDialog(
            title = pref.title(),
            options = pref.options,
            optionLabel = pref.optionLabel,
            selected = pref.value,
            multiSelect = false,
            onDismiss = { showDialog = false },
            onConfirm = { confirmed ->
                showDialog = false
                @Suppress("UNCHECKED_CAST")
                (confirmed as? T)?.let(pref.onValueChange)
            }
        )
    }
}
