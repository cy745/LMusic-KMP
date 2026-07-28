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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lalilu.common.settings.ClickPreference
import com.lalilu.common.settings.PreferenceActionContext


/**
 * Click 偏好项的 Material3 行实现。
 *
 * ## 视觉约定（与项目内其他列表行统一）
 *
 * - `Column.padding(horizontal=16dp, vertical=12dp)`，无 Card 外壳
 * - 标题 `bodyLarge` + `onBackground`
 * - 副标题 `bodySmall` 12sp + `onBackground.copy(0.6f)`
 *
 * ## 行为
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
}
