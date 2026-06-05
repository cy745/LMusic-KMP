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

package com.lalilu.common.testing

import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication


/**
 * 启动一个临时 Koin 容器，运行 [block]，结束后自动 close。
 *
 * 适合 [com.lalilu.common.settings.SettingsCollector] 等依赖 Koin 全局实例
 * 但又不想污染其他测试的场景。
 *
 * 用法：
 * ```
 * testKoin(module { single<Foo> { Foo() } }) {
 *     val foo = get<Foo>()
 *     ...
 * }
 * ```
 */
inline fun <T> testKoin(
    vararg modules: Module,
    block: Koin.() -> T
): T {
    lateinit var koinApp: KoinApplication
    try {
        koinApp = koinApplication { modules(*modules) }
        return koinApp.koin.block()
    } finally {
        koinApp.close()
    }
}
