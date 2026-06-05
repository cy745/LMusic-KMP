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

package com.lalilu.common.kv

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 反应式 + 可代理的"键值项"抽象。
 *
 * 复合了三个角色：
 * - [MutableState]：[value] 由 `mutableStateOf` 包装，Compose 读取时建立快照订阅
 * - [ReadWriteProperty]：可作为 `by` 委托属性使用
 * - [UpdatableKV]：暴露 `flow()` / `save()` / `update()` 等手动控制接口
 *
 * ## key 字段
 *
 * 子类必须实现 [key]，为该 KVItem 在所属 prefix 命名空间内的全名。
 * 主要给外部模块用做稳定标识（如 `com.lalilu.common.settings.Preference` 体系
 * 会拿 [key] 当成持久化 key）。
 */
abstract class KVItem<T> : MutableState<T>, ReadWriteProperty<KVItem<T>, T>, UpdatableKV<T> {
    /** 该项在所属 prefix 命名空间内的全名（通常为 `${prefix}_${name}`）。 */
    abstract val key: String

    var autoSave = true
        private set

    private val state: MutableState<T> by lazy { mutableStateOf(getData()) }
    private val flowInternal: MutableStateFlow<T> by lazy { MutableStateFlow(state.value) }

    override var value: T
        get() = state.value
        set(value) {
            val oldValue = state.value
            state.value = value
            if (oldValue != value && autoSave) {
                setData(value)
            }
        }

    override fun getValue(thisRef: KVItem<T>, property: KProperty<*>): T = thisRef.value
    override fun setValue(thisRef: KVItem<T>, property: KProperty<*>, value: T) =
        run { thisRef.value = value }

    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }

    override fun save() = setData(value)
    override fun update() = run { value = getData() }
    override fun flow(): Flow<T> = flowInternal
    override fun enableAutoSave() = run { autoSave = true }
    override fun disableAutoSave() = run { autoSave = false }

    override fun setData(value: T) {
        flowInternal.tryEmit(value)
    }
}
