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

package com.lalilu.common.settings

import androidx.compose.runtime.Composable


/**
 * 一组 [Preference] 的容器，仿照 Android `PreferenceScreen` 的概念。
 *
 * 每一个 [SettingsGroup] 在设置页里渲染为一个标题卡片 + 一组偏好项。
 *
 * ## 排序与懒求值
 *
 * - [order] 控制跨 group 的渲染顺序（升序）；app 级配置建议 `-1000`，具体
 *   业务模块可选用 `0` ~ `100`。
 * - [preferences] 用 lambda 延迟构造，避免模块初始化时立即拉取 Koin 依赖
 *   或计算密集型工作；只在 `SettingsScreenContent` 真正渲染时被调用。
 * - [key] 应当全局唯一，便于 [SettingsCollector.getByKey] 查询。
 *
 * ## 使用范本
 *
 * ```
 * @Factory
 * @Named("settings_lplayer")
 * fun provideLPlayerSettings(): SettingsGroup = settingsGroup(
 *     key = "lplayer", order = 10, title = { "播放器" },
 * ) {
 *     switch(LPlayerKV.autoPlayWhenRestart, title = { "启动后自动播放" })
 *     click("lplayer.clear_queue", title = { "清空队列" }, onClick = { ... })
 * }
 * ```
 */
data class SettingsGroup(
    val key: String,
    val order: Int = 0,
    val title: @Composable () -> String? = { null },
    val description: @Composable () -> String? = { null },
    val preferences: () -> List<Preference<*>>,
)
