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

package com.lalilu.lplayer

import com.lalilu.common.settings.NoOpToaster
import com.lalilu.common.settings.SettingsGroup
import com.lalilu.common.settings.settingsGroup
import com.lalilu.lplayer.extensions.PlayMode
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named


/**
 * 播放器模块贡献的 [SettingsGroup]。
 *
 * 用 `@Factory` + `@Named("settings_lplayer")` 注入到 Koin，
 * 由 :lsettings 模块的 [com.lalilu.common.settings.SettingsCollector] 收集后渲染。
 *
 * 当前包含 5 个偏好项：
 *
 * | key                                       | 类型      | 持久化字段                  |
 * |-------------------------------------------|-----------|-----------------------------|
 * | `lplayer_autoPlayWhenRestart`             | Switch    | `LPlayerKV.autoPlayWhenRestart` |
 * | `lplayer_handleAudioFocus`                | Switch    | `LPlayerKV.handleAudioFocus`    |
 * | `lplayer_handleBecomeNoisy`               | Switch    | `LPlayerKV.handleBecomeNoisy`   |
 * | `lplayer_playMode`                        | Dropdown  | `LPlayerKV.playMode` (String)   |
 * | `lplayer.clear_history_position`          | Click     | 无，点击时重置 `LPlayerKV.historyPlayPosition` |
 */
@Factory
@Named("settings_lplayer")
fun provideLPlayerSettings(): SettingsGroup = settingsGroup(
    key = "lplayer",
    order = 10,
    title = { "播放器" },
) {
    switch(
        kv = LPlayerKV.autoPlayWhenRestart,
        title = { "启动后自动播放" },
        summary = { "应用启动后自动恢复上次的播放状态" }
    )
    switch(
        kv = LPlayerKV.handleAudioFocus,
        title = { "处理音频焦点" },
        summary = { "其他应用播放音频时自动暂停" }
    )
    switch(
        kv = LPlayerKV.handleBecomeNoisy,
        title = { "监听耳机拔出" },
        summary = { "拔出耳机时自动暂停播放" }
    )
    dropdown(
        kv = LPlayerKV.playMode,
        title = { "播放模式" },
        options = PlayMode.entries,
        optionLabel = { mode ->
            when (mode) {
                PlayMode.ListRecycle -> "列表循环"
                PlayMode.RepeatOne   -> "单曲循环"
                PlayMode.Shuffle     -> "随机播放"
            }
        },
        serialize = { it.name },
        deserialize = { name -> PlayMode.from(name) },
        fallback = PlayMode.ListRecycle
    )
    click(
        key = "lplayer.clear_history_position",
        title = { "清除播放进度记录" },
        summary = { "下次启动将从头开始播放" },
        onClick = { ctx ->
            LPlayerKV.historyPlayPosition.value = 0L
            // 仅在业务侧注入了真实 Toaster 时才反馈（默认 NoOpToaster）
            (ctx.toaster.takeIf { it !== NoOpToaster })?.info("已清除")
        }
    )
}
