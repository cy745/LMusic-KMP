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

data class SortedGroup<T>(
    val groupId: GroupId?,
    val items: List<T>,
    val extras: List<ItemExtraData?> = emptyList()
)

data class SortResult<T>(
    val groups: List<SortedGroup<T>>,
) {
    val itemList: List<T> by lazy { groups.flatMap { it.items } }

    inline fun draw(onGroup: SortedGroup<T>.() -> Unit) {
        groups.forEach { group -> group.onGroup() }
    }

    companion object {
        @Stable
        fun <T> flat(items: List<T>): SortResult<T> =
            SortResult(listOf(SortedGroup(null, items)))

        @Stable
        inline fun <reified T> empty(): SortResult<T> = SortResult(emptyList())
    }
}