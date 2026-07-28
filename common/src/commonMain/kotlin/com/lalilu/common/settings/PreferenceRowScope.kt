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
 * 提供给 [Preference.customRenderer] / [CustomPreference.content] 的 Composable 上下文。
 *
 * - [preference] 拿到当前渲染中的 [Preference] 实例
 * - [set] 是 [onValueChange] 的便利调用，等价于 `pref.onValueChange(v)`
 *
 * 使用示例：
 * ```
 * customPreference("k", { "Demo" }, 0, {}, content = {
 *     Button(onClick = { set(42) }) { Text("Set to 42") }
 * })
 * ```
 */
interface PreferenceRowScope {
    val preference: Preference<*>

    fun <V> Preference<V>.set(newValue: V) {
        onValueChange(newValue)
    }
}


/**
 * 默认实现：直接把 [Preference] 引用作为 [preference] 暴露。
 *
 * 类本身 `public`（方便跨模块传递），但**构造器 `internal`**——只有
 * 同模块（即 common）内可 `new`，业务模块无法直接构造。
 * 业务方应通过 [CustomPreference.content] 闭包使用。
 */
class PreferenceRowScopeImpl internal constructor(
    override val preference: Preference<*>
) : PreferenceRowScope


/**
 * 跨模块可访问的工厂函数。
 *
 * 为什么不直接让构造器 public？构造器是 common 模块的"私有实现细节"，
 * 业务模块应通过 [CustomPreference.content] 闭包拿到 `this` 即可。
 * :lsettings 等同模块需要构造时走这个工厂。
 */
fun newPreferenceRowScope(pref: Preference<*>): PreferenceRowScope =
    PreferenceRowScopeImpl(pref)
