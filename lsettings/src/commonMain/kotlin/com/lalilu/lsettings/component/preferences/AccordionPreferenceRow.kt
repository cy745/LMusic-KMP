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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import com.lalilu.common.settings.AccordionPreference
import com.lalilu.lsettings.component.LocalPreferenceRenderers
import com.lalilu.lsettings.component.render
import org.jetbrains.compose.resources.vectorResource

/**
 * 可折叠分组偏好项的 Material3 行实现。
 *
 * ## 视觉约定（与项目内其他列表行统一）
 *
 * - 折叠头 `Row.padding(horizontal=16dp, vertical=12dp)`：标题 `bodyLarge` +
 *   副标题 `bodySmall` 12sp + `onBackground.copy(0.6f)`，尾部 180° 旋转箭头
 * - 展开动画 [AnimatedVisibility]（fade + expand/shrink）
 * - 展开后子项逐个交给 [com.lalilu.lsettings.component.PreferenceRenderers] 递归分发，
 *   渲染样式与平铺时完全一致
 *
 * ## 行为
 *
 * - 点击折叠头切换展开/收起；展开状态为纯 UI 状态（不持久化）
 * - 子项各自的 [com.lalilu.common.settings.Preference.visible] 过滤在展开时生效
 *
 * 测试 tag：`preference_accordion_<key>`（头部）
 */
@Composable
fun AccordionPreferenceRow(
    pref: AccordionPreference,
    modifier: Modifier = Modifier
) {
    val renderers = LocalPreferenceRenderers.current
    // rememberSaveable：LazyColumn 滚动回收 item 后恢复展开状态
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preference_accordion_${pref.key}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
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

            Icon(
                imageVector = vectorResource(RemixIcon.Arrows.arrowDownSLine),
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                pref.children.forEach { child ->
                    if (child.visible()) {
                        renderers.render(
                            pref = child,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
