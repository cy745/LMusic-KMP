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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lalilu.common.settings.SliderPreference


/**
 * Slider 偏好项的 Material3 行实现。
 *
 * ## 视觉约定（与项目内其他列表行统一）
 *
 * - 标题 `bodyLarge` + `onBackground`
 * - 副标题 `bodySmall` 12sp + `onBackground.copy(0.6f)`
 * - 当前值 `labelMedium` + `primary`（高亮当前值）
 * - 整体 `padding(horizontal=16dp, vertical=12dp)`
 *
 * ## 行为
 *
 * 拖动过程中仅更新本地 `localValue`（避免高频写盘 + 频繁重组），
 * `onValueChangeFinished` 时再正式通过 [SliderPreference.onValueChange] 提交，
 * 由 `writeBack` 写穿到 KV / 业务方。
 *
 * 测试 tag：`preference_slider_<key>`
 */
@Composable
fun SliderPreferenceRow(
    pref: SliderPreference,
    modifier: Modifier = Modifier
) {
    val isEnabled = pref.enabled()
    var localValue by remember(pref.value) { mutableStateOf(pref.value) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preference_slider_${pref.key}")
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
            text = pref.valueLabel(localValue),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = {
                if (isEnabled) pref.onValueChange(localValue)
            },
            valueRange = pref.valueRange,
            steps = pref.steps,
            enabled = isEnabled
        )
    }
}
