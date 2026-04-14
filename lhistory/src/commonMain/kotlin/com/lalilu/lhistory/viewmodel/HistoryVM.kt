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

package com.lalilu.lhistory.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.lalilu.extensions.toState
import com.lalilu.lhistory.repository.HistoryRepository
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAudio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single
class HistoryVM(
    val historyRepo: HistoryRepository
) : ViewModel() {
    val historyState = historyRepo
        .getHistoriesIdsMapWithLastTime()
        .flatMapLatest { map ->
            val ids = map.toList()
                .sortedByDescending { it.second }
                .map { it.first }
            LMedia.instance.mapByFlow<LAudio>(ids)
        }.map { it.take(6) }
        .toState(emptyList(), viewModelScope)

    val pager = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = true,
        ),
        pagingSourceFactory = { historyRepo.getAllData() }
    ).flow.cachedIn(viewModelScope)

    fun getHistoryPlayedIds(block: (list: List<String>) -> Unit) = viewModelScope.launch {
        val list = historyRepo.getHistoriesIdsMapWithLastTime()
            .firstOrNull()
            ?.toList()
            ?.sortedByDescending { it.second }
            ?.map { it.first }
            ?: emptyList()
        block(list)
    }
}