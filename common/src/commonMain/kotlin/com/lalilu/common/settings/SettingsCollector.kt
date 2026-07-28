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

import org.koin.mp.KoinPlatform


/**
 * 收集所有已注册的 [SettingsGroup]。
 *
 * ## 设计要点
 *
 * - 业务模块用 `@Factory` + `@Named("settings_xxx")` 把 [SettingsGroup] 注入 Koin。
 * - 顶层 [com.lalilu.lsettings.SettingsScreen] 渲染时通过 [collectAll]
 *   一次性拉取所有 group 并按 [SettingsGroup.order] 升序排序。
 * - order 相同时按 `key` 字典序兜底，保证稳定的渲染顺序。
 * - 不依赖任何 UI 模块——Koin + KMP 兼容。
 *
 * ## 收集 vs 注入的边界
 *
 * 这种"全平台收集"只在 [com.lalilu.lsettings.SettingsScreen] 内部调用，
 * 业务模块不需要直接使用 [collectAll]；它们只负责把 [SettingsGroup]
 * 用 `@Factory` 注册即可。
 */
object SettingsCollector {

    /**
     * 拉取当前 Koin 容器中所有 [SettingsGroup] 实例，并按
     * `(order ASC, key ASC)` 排序。
     */
    fun collectAll(): List<SettingsGroup> =
        KoinPlatform.getKoin()
            .getAll<SettingsGroup>()
            .sortedWith(compareBy({ it.order }, { it.key }))

    /**
     * 按 key 查询单个 group，未找到时返回 `null`。
     *
     * 主要用于：调试时定位某个 group，或未来"按需跳转设置分组"等场景。
     */
    fun getByKey(key: String): SettingsGroup? =
        KoinPlatform.getKoin()
            .getAll<SettingsGroup>()
            .firstOrNull { it.key == key }
}
