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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.common.settings.Preference
import com.lalilu.common.settings.SettingsGroup
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.lsettings.component.LocalPreferenceRenderers
import com.lalilu.lsettings.component.render
import com.lalilu.navigation.smartbar.NavigatorHeader


/**
 * 设置页主体的可重用 Composable。
 *
 * ## 视觉约定（与项目内其他页面统一）
 *
 * - 顶部 [NavigatorHeader] 与 `HistoryScreen` / `SongDetailScreen` 同款
 * - `contentPadding` 计算 `statusBars + 16.dp` / `smartBarHeight + 16.dp`，与其他列表页一致
 * - 分组标题采用 `SongsScreen` / `MediaSourceScreen` 的 inline Column 模式：
 *   `titleMedium` (16sp) + `SemiBold` + `onBackground` + 12sp 副标题 `onBackground.copy(0.6f)`
 *   形成 `22sp(page) → 16sp(group) → 14sp(item)` 三级字号层级
 * - 单条偏好项**不再**用 Material3 `Card` 包裹，直接 `Row.padding(16, 12)`，
 *   颜色 / 间距与其他列表项一致
 *
 * ## 行为约定
 *
 * - 接收的 [groups] 应当**预先排好序**（建议直接用 [com.lalilu.common.settings.SettingsCollector.collectAll]）
 * - `preferences()` lambda 在首次组合时被调用并缓存；后续重组复用
 * - [Preference.visible] 返回 `false` 的项**整行不渲染**
 * - [Preference.enabled] 返回 `false` 的项**仍渲染但禁用交互**
 *
 * ## 测试入口
 *
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showNavigatorHeader: Boolean = true,
) {
    val renderers = LocalPreferenceRenderers.current

    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

    // 预计算可见偏好项；remember(groups) 保证 groups 引用不变时不会重算
    val data: List<GroupData> = remember(groups) {
        groups.map { group ->
            GroupData(
                group = group,
                visiblePrefs = group.preferences().filter { it.visible() }
            )
        }
    }

    val effectiveContentPadding = if (contentPadding == PaddingValues(0.dp)) {
        PaddingValues(
            top = statusBar.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp
        )
    } else {
        contentPadding
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_content"),
        contentPadding = effectiveContentPadding
    ) {
        if (showNavigatorHeader) {
            item(
                key = "settings_navigator_header",
                contentType = "navigator_header"
            ) {
                // 自定义 paddingValues：
                //   LazyColumn 顶层 contentPadding 已经预留了 statusBar + 16.dp
                //   NavigatorHeader 默认 top=24.dp 会导致累计偏移 = statusBar + 40.dp（偏低）
                //   这里把 top 改为 8.dp，使累计 = statusBar + 24.dp
                //   与 HistoryScreen / SongsScreen / MediaSourceScreen 三种头部模式保持一致
                NavigatorHeader(
                    modifier = Modifier.testTag("settings_navigator_header"),
                    title = "设置",
                    subTitle = "应用与各模块偏好",
                    paddingValues = PaddingValues(
                        top = 8.dp,
                        bottom = 20.dp,
                        start = 20.dp,
                        end = 20.dp
                    )
                )
            }
        }

        data.forEach { entry ->
            // group header item —— inline Column 风格，与 SongsScreen / MediaSourceScreen 一致
            // 字号使用 titleMedium (16sp) / SemiBold，与 page header (titleLarge 22sp / Black)
            // 拉开层级：22sp → 16sp → 14sp(item body)
            item(
                key = "settings_group_header_${entry.group.key}",
                contentType = "group_header"
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("settings_group_header_${entry.group.key}")
                ) {
                    entry.group.title?.invoke()?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    entry.group.description?.invoke()?.let { desc ->
                        Text(
                            text = desc,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                        )
                    }
                }
            }

            // visible preferences —— 普通 Row（不再用 Card 包裹），与其他列表项统一
            items(
                items = entry.visiblePrefs,
                key = { pref -> "settings_group_${entry.group.key}_pref_${pref.key}" },
                contentType = { "preference" }
            ) { pref ->
                renderers.render(
                    pref = pref,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("settings_preference_${pref.key}")
                )
            }
        }
    }
}


/** `SettingsScreenContent` 内部使用的"已过滤可见项"快照。 */
private data class GroupData(
    val group: SettingsGroup,
    val visiblePrefs: List<Preference<*>>
)
