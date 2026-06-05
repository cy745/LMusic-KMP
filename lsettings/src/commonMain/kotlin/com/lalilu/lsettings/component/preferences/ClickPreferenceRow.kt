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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lalilu.common.settings.ClickPreference
import com.lalilu.common.settings.PreferenceActionContext


/**
 * Click 偏好项的 Material3 行实现。
 *
 * - 整行可点击，触发 [ClickPreference.onClick]
 * - disabled 时点击不响应
 * - 当前实现使用 [PreferenceActionContext.Empty]（无 toaster / 无 navigate），
 *   业务侧若需要真实反馈，可将 [ClickPreference.onClick] 改为接受真实 Context
 *   并在 `SettingsScreen` 处构造；本行组件只负责转发。
 *
 * 测试 tag：`preference_click_<key>`
 */
@Composable
fun ClickPreferenceRow(
    pref: ClickPreference,
    modifier: Modifier = Modifier
) {
    val isEnabled = pref.enabled()
    val context = remember { PreferenceActionContext.Empty }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preference_click_${pref.key}")
            .clickable(enabled = isEnabled) { pref.onClick(context) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = pref.title(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        pref.summary?.invoke()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
