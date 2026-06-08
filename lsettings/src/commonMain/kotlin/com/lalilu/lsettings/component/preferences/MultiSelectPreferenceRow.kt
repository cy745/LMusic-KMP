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
import com.lalilu.common.settings.MultiSelectPreference
import com.lalilu.lsettings.dialog.ListSelectDialog


/**
 * 多选下拉偏好项的 Material3 行实现。
 *
 * 与 [DropdownPreferenceRow] 共用 [ListSelectDialog]，只是以多选模式打开。
 *
 * ## 视觉约定（与项目内其他列表行统一）
 *
 * - `Row.padding(horizontal=16dp, vertical=12dp)`，无 Card 外壳
 * - 标题 / 副标题样式与 [DropdownPreferenceRow] 一致
 * - 尾部显示 `"已选 N / 总 M"` 紧凑表达，样式 `bodyMedium` + `primary`
 * - 行内 `Arrangement.spacedBy(12.dp)`
 *
 * 测试 tag：`preference_multiselect_<key>`
 */
@Composable
fun <T : Any> MultiSelectPreferenceRow(
    pref: MultiSelectPreference<T>,
    modifier: Modifier = Modifier
) {
    val isEnabled = pref.enabled()
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preference_multiselect_${pref.key}")
            .clickable(enabled = isEnabled) { showDialog = true }
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
        Text(
            text = "${pref.value.size} / ${pref.options.size}",
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
            multiSelect = true,
            onDismiss = { showDialog = false },
            onConfirm = { confirmed ->
                showDialog = false
                @Suppress("UNCHECKED_CAST")
                (confirmed as? Set<T>)?.let(pref.onValueChange)
            }
        )
    }
}
