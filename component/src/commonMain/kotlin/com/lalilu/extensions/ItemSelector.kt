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

package com.lalilu.extensions

import androidx.compose.runtime.*

@Stable
class ItemSelector<T> {
    private val items = mutableStateOf(emptySet<T>())
    private val _isSelecting = mutableStateOf(false)
    val isSelecting: MutableState<Boolean> = object : MutableState<Boolean> {
        override var value: Boolean
            get() = _isSelecting.value
            set(value) = run { if (!value) clear(); _isSelecting.value = value }

        override fun component1(): Boolean = value
        override fun component2(): (Boolean) -> Unit = { value = it }
    }

    fun isSelected(item: T) = items.value.contains(item)
    fun selected() = items.value

    fun onSelect(item: T) {
        if (!isSelecting.value) isSelecting.value = true

        if (items.value.contains(item)) items.value -= item
        else items.value += item
    }

    fun selectAll(list: List<T>) {
        if (!isSelecting.value) isSelecting.value = true
        items.value = list.toSet()
    }

    fun clear() = run { items.value = emptySet() }
}

@Composable
fun <T> rememberSelector(): ItemSelector<T> {
    return remember { ItemSelector() }
}