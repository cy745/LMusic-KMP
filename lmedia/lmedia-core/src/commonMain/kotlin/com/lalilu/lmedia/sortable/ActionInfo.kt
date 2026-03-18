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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

@Stable
data class ActionInfo(
    val title: String,
    val subTitle: String? = null,
    val icon: Painter? = null
)

interface SortAction {
    fun key(): String? = null

    @Stable
    @Composable
    fun getActionInfo(): ActionInfo = ActionInfo("")

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T : Sortable> doSort(
        items: Flow<List<T>>,
        config: SortConfig = SortConfig()
    ): Flow<SortResult<T>> = items
        .mapLatest { doSortInternal(it, config) }
        .mapLatest { if (config.hideGroup) SortResult.flat(it.itemList) else it }

    suspend fun <T : Sortable> doSortInternal(
        items: List<T>,
        config: SortConfig = SortConfig()
    ): SortResult<T> = items.let {
        SortResult.flat(if (config.reverse) it.asReversed() else it)
    }
}