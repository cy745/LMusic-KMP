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

package com.lalilu.lsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lalilu.common.settings.Preference
import com.lalilu.common.settings.SettingsGroup
import com.lalilu.lsettings.component.LocalPreferenceRenderers
import com.lalilu.lsettings.component.render


/**
 * 设置页主体的可重用 Composable。
 *
 * 行为约定：
 * - 接收的 [groups] 应当**预先排好序**（建议直接用 [com.lalilu.common.settings.SettingsCollector.collectAll]）
 * - `preferences()` lambda 在首次组合时被调用并缓存；后续重组复用
 * - [Preference.visible] 返回 `false` 的项**整行不渲染**
 * - [Preference.enabled] 返回 `false` 的项**仍渲染但禁用交互**
 *
 * 同时也是测试 `runComposeUiTest` 验证的入口：
 * ```
 * runComposeUiTest {
 *     setContent { SettingsScreenContent(groups = listOf(g1, g2)) }
 *     onAllNodesWithTag("settings_group_header_g1").assertCountEquals(1)
 * }
 * ```
 */
@Composable
fun SettingsScreenContent(
    groups: List<SettingsGroup>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val renderers = LocalPreferenceRenderers.current

    // 预计算可见偏好项；remember(groups) 保证 groups 引用不变时不会重算
    val data: List<GroupData> = remember(groups) {
        groups.map { group ->
            GroupData(
                group = group,
                visiblePrefs = group.preferences().filter { it.visible() }
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_content"),
        contentPadding = contentPadding
    ) {
        data.forEach { entry ->
            // group header item
            item(
                key = "settings_group_header_${entry.group.key}",
                contentType = "group_header"
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("settings_group_header_${entry.group.key}")
                ) {
                    entry.group.title?.invoke()?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    entry.group.description?.invoke()?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // visible preferences
            items(
                items = entry.visiblePrefs,
                key = { pref -> "settings_group_${entry.group.key}_pref_${pref.key}" },
                contentType = { "preference" }
            ) { pref ->
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("settings_preference_${pref.key}")
                ) {
                    renderers.render(pref)
                }
            }
        }
    }
}


/** `SettingsScreenContent` 内部使用的"已过滤可见项"快照。 */
private data class GroupData(
    val group: SettingsGroup,
    val visiblePrefs: List<Preference<*>>
)
