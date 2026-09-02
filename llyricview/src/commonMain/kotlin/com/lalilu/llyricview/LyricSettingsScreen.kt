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

package com.lalilu.llyricview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lalilu.RemixIcon
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lfont.manager.FontManager
import com.lalilu.lsettings.SettingsSubpageContent
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenInfo
import com.lalilu.navigation.ScreenInfoFactory
import org.koin.compose.koinInject


/**
 * 歌词设置子页。
 *
 * 路由：`/settings/lyric`（KRouter）
 *
 * ## 架构定位
 *
 * - 完整歌词设置不再注册进主设置页（避免堆积），由本子页承载；
 *   主设置页只保留 [provideLyricSettingsEntry] 的 click 入口跳转过来。
 * - 页面结构（Screen class / 路由 / ScreenInfo）由本模块自行构建，
 *   渲染委托给 lsettings 的 [SettingsSubpageContent] 模版。
 * - 播放页弹窗的快捷设置走 [provideLyricSettingsQuick]（精简版）。
 */
@Destination("/settings/lyric")
data object LyricSettingsScreen : Screen, ScreenInfoFactory {

    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(
            title = { "歌词设置" },
            icon = RemixIcon.Editor.text
        )
    }

    @Composable
    override fun Content() {
        SettingsSubpageContent(
            title = "歌词设置",
            subTitle = "歌词显示样式",
            groups = provideLyricSettings(fontManager = koinInject())
        )
    }
}
