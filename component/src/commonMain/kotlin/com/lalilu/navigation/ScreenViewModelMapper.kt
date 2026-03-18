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

package com.lalilu.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntryDecorator
import org.koin.compose.currentKoinScope
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.viewmodel.defaultExtras

/**
 * 用于存储屏幕密钥与 ViewModelStoreOwner 映射关系的可变状态地图。
 * 键为屏幕的唯一标识符，值为对应的 ViewModelStoreOwner 实例。
 */
private val mapOfViewModelStore = mutableStateMapOf<Any, ViewModelStoreOwner>()

/**
 * 根据给定的密钥获取对应的 ViewModelStoreOwner。
 *
 * @param key 屏幕的唯一标识符。
 * @return 如果存在则返回对应的 ViewModelStoreOwner，否则返回 null。
 */
fun getScreenViewModelStoreOwner(key: Any): ViewModelStoreOwner? {
    return mapOfViewModelStore[key]
}

/**
 * 一个导航条目装饰器，用于在导航条目进入时将其当前的 ViewModelStoreOwner
 * 注册到全局映射中，并在条目弹出时自动移除该映射关系。
 *
 * @param T 导航内容数据的类型。
 */
class ViewModelStoreMapperNavEntryDecorator<T : Any> : NavEntryDecorator<T>(
    onPop = { key -> mapOfViewModelStore.remove(key) },
    decorate = { entry ->
        val viewModelStore = LocalViewModelStoreOwner.current
        if (viewModelStore != null) {
            mapOfViewModelStore[entry.contentKey] = viewModelStore
        }
        entry.Content()
    }
)

/**
 * 记住并创建一个 [ViewModelStoreMapperNavEntryDecorator] 实例。
 * 该函数确保在组合生命周期内只创建一次装饰器实例。
 *
 * @return 一个 [ViewModelStoreMapperNavEntryDecorator] 实例。
 */
@Composable
fun <T : Any> rememberViewModelStoreMapperNavEntryDecorator(): ViewModelStoreMapperNavEntryDecorator<T> {
    return remember { ViewModelStoreMapperNavEntryDecorator() }
}

/**
 * 一个用于在 [Screen] 上下文中获取 Koin ViewModel 的辅助函数。
 *
 * 该函数扩展了 [Screen] 类，允许直接通过屏幕实例获取关联的 ViewModel。
 * 它首先尝试从全局映射 [mapOfViewModelStore] 中查找与当前屏幕密钥 ([Screen.key]) 绑定的
 * [ViewModelStoreOwner]。如果未找到，则回退到使用当前的 [LocalViewModelStoreOwner]。
 * 如果两者均不可用，则抛出异常。
 *
 * @param T 要获取的 ViewModel 类型。
 * @param qualifier 可选的 Koin 限定符，用于区分同类型的多个 ViewModel 定义。
 * @param viewModelStoreOwner 提供 ViewModel 存储的所有者。默认情况下，它会尝试从全局映射中
 *        根据当前屏幕的 key 获取，若不存在则使用组合上下文中的当前所有者。
 * @param key 用于标识 ViewModel 的唯一键。默认为当前屏幕的 [Screen.key]。
 * @param extras 用于创建 ViewModel 的额外参数。默认为基于 [viewModelStoreOwner] 生成的默认值。
 * @param scope 当前的 Koin 作用域。默认为通过 `currentKoinScope()` 获取的作用域。
 * @param parameters 可选的构造函数参数定义，用于向 ViewModel 传递运行时参数。
 * @return 类型为 [T] 的 ViewModel 实例。
 */
@Composable
inline fun <reified T : ViewModel> Screen.koinScreenViewModel(
    qualifier: Qualifier? = null,
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(
        value = getScreenViewModelStoreOwner(this.key) ?: LocalViewModelStoreOwner.current,
        lazyMessage = { "No ViewModelStoreOwner was provided for ${T::class}" }
    ),
    key: String? = this.key,
    extras: CreationExtras = defaultExtras(viewModelStoreOwner),
    scope: Scope = currentKoinScope(),
    noinline parameters: ParametersDefinition? = null,
): T = koinViewModel<T>(
    qualifier = qualifier,
    viewModelStoreOwner = viewModelStoreOwner,
    key = key,
    extras = extras,
    scope = scope,
    parameters = parameters
)