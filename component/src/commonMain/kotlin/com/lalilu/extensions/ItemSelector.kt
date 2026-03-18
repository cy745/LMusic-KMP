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