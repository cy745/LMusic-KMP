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
