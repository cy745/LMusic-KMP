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

package com.lalilu.lmedia.sortable

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable


@Serializable
sealed class GroupId {
    @Serializable
    data object None : GroupId() {
        private fun readResolve(): Any = None
    }

    @Serializable
    data class FirstLetter(val letter: String) : GroupId()
    data class DiskNumber(val number: Int) : GroupId()

    @Serializable
    data class Time(val time: String) : GroupId()

    val text: String by lazy {
        when (this) {
            is DiskNumber -> number.toString()
            is FirstLetter -> letter
            is Time -> time
            None -> "NONE"
        }
    }

    override fun toString(): String = text
}

interface ItemExtraData {
    data class TrackNumber(val number: Int) : ItemExtraData
    data class PlayedCount(val count: Int) : ItemExtraData
}

data class SortedGroup<T : Sortable>(
    val groupId: GroupId?,
    val items: List<T>,
    val extras: List<ItemExtraData?> = emptyList()
)

sealed interface SortResult<T : Sortable> {
    val itemList: List<T>

    data class Grouped<T : Sortable>(val groups: List<SortedGroup<T>>) : SortResult<T> {
        override val itemList: List<T> by lazy { groups.flatMap { it.items } }
    }

    data class Flat<T : Sortable>(val items: List<T>) : SortResult<T> {
        override val itemList: List<T> = items
    }

    companion object {
        @Stable
        inline fun <reified T : Sortable> empty(): SortResult<T> = Flat(emptyList())
    }
}