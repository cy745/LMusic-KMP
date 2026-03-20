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

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Stable
import com.lalilu.common.ext.SerializableObject


@Serializable
sealed class GroupId : SerializableObject {
    @Serializable
    data object None : GroupId() {
        private fun readResolve(): Any = None
    }

    @Serializable
    data class FirstLetter(val letter: String) : GroupId()

    @Serializable
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

data class SortResult<T : Sortable>(
    val groups: List<SortedGroup<T>>,
) {
    val itemList: List<T> by lazy { groups.flatMap { it.items } }

    inline fun draw(onGroup: SortedGroup<T>.() -> Unit) {
        groups.forEach { group -> group.onGroup() }
    }

    companion object {
        @Stable
        fun <T : Sortable> flat(items: List<T>): SortResult<T> =
            SortResult(listOf(SortedGroup(null, items)))

        @Stable
        inline fun <reified T : Sortable> empty(): SortResult<T> = SortResult(emptyList())
    }
}