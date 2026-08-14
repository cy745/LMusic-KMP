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

package com.lalilu.lsettings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.common.settings.SettingsGroup


/**
 * 设置子页模版。
 *
 * 供业务模块构建**独立的设置子页**（而非把设置项堆积到主设置页）：
 *
 * ```
 * @Destination("/settings/lyric")
 * data object LyricSettingsScreen : Screen, ScreenInfoFactory {
 *     @Composable
 *     override fun Content() {
 *         SettingsSubpageContent(
 *             title = "歌词设置",
 *             subTitle = "歌词显示样式",
 *             groups = provideLyricSettings()
 *         )
 *     }
 * }
 * ```
 *
 * ## 设计约定
 *
 * - 页面相关部分（Screen class / `@Destination` 路由 / ScreenInfo 等）由模块
 *   内自行构建并经 KRouter 注册发现；本模版只负责 Compose 页面渲染。
 * - 模块内自行构建设置项（[SettingsGroup]）并传入本模版渲染，
 *   **不需要**通过 `@Factory` + `@Named("settings_xxx")` 自动注册进主设置页
 *   （主设置页只保留 click 入口，点击后跳转到对应子页）。
 * - 内部委托 [SettingsScreenContent]：NavigatorHeader（标题/副标题）+
 *   分组渲染，视觉与主设置页一致。
 */
@Composable
fun SettingsSubpageContent(
    title: String,
    subTitle: String? = null,
    groups: List<SettingsGroup>,
    modifier: Modifier = Modifier,
) {
    SettingsScreenContent(
        groups = groups,
        modifier = modifier,
        showNavigatorHeader = true,
        headerTitle = title,
        headerSubTitle = subTitle.orEmpty(),
    )
}
