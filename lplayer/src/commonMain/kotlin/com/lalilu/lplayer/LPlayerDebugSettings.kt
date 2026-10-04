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

package com.lalilu.lplayer

import com.lalilu.common.settings.SettingsGroup
import com.lalilu.common.settings.settingsGroup
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

/**
 * 调试能力贡献的 [SettingsGroup]。
 *
 * 用 `@Factory` + `@Named("settings_lplayer_debug")` 注入 Koin，由
 * [com.lalilu.common.settings.SettingsCollector] 收集后渲染。order 取较大值，
 * 排在用户向设置之后。
 *
 * 目前只有一项：悬浮加载测试面板开关（[LPlayerKV.debugFloatingPanel]）。开启后
 * 任意页面右上角出现时序注入面板，用于复现封面/歌词/时长/缓冲的到达顺序；注入只
 * 改变展示层的时间参数，不改变真实取源与播放判定。
 */
@Factory
@Named("settings_lplayer_debug")
fun provideLPlayerDebugSettings(): SettingsGroup = settingsGroup(
    key = "lplayer_debug",
    order = 900,
    title = { "调试" },
    description = { "复现加载过渡时序时使用，正常播放无需开启" },
) {
    switch(
        kv = LPlayerKV.debugFloatingPanel,
        title = { "悬浮加载测试面板" },
        summary = { "在任意页面右上角显示时序注入面板；只影响界面表现，不改变真实取源与播放" },
    )
}
