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

package com.lalilu.lalbum.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.MviWithIntent
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.toState
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.sortable.SortAction
import com.lalilu.lmedia.sortable.SortConfig
import com.lalilu.lmedia.sortable.SortManager
import com.lalilu.lmedia.sortable.doSortState
import com.lalilu.mviImplWithIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@Stable
@Immutable
data class AlbumsState(
    // control flags
    val showSearcherPanel: Boolean = false,
    val showSortPanel: Boolean = false,

    // control params
    val searchKeyWord: String = "",
    val showText: Boolean = true,
) {
    val distinctKey: Int = searchKeyWord.hashCode()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAlbumsFlow(): Flow<List<LAlbum>> {
        val source = LMedia.instance.flow<LAlbum>()

        val keywords: List<String> = when {
            searchKeyWord.isBlank() -> emptyList()
            searchKeyWord.contains(' ') -> searchKeyWord.split(' ')
            else -> listOf(searchKeyWord)
        }

        return source.mapLatest { flow ->
            flow.filter { item -> keywords.all { item.getMatchText().contains(it) } }
        }
    }
}

sealed interface AlbumsAction {
    data object ToggleSortPanel : AlbumsAction
    data object ToggleSearcherPanel : AlbumsAction
    data object ToggleShowText : AlbumsAction

    data object HideSortPanel : AlbumsAction
    data object HideSearcherPanel : AlbumsAction

    data class SearchFor(val keyword: String) : AlbumsAction
    data class SelectSortAction(val action: SortAction) : AlbumsAction
    data class UpdateSortConfig(val config: SortConfig) : AlbumsAction
}

sealed interface AlbumsEvent

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class AlbumsVM : ViewModel(),
    MviWithIntent<AlbumsState, AlbumsEvent, AlbumsAction>
    by mviImplWithIntent(AlbumsState()) {

    val sorter = SortManager(
        prefix = "albums_",
        supportedActions = requestFor<SortAction>(
            "sort_rule_normal",
            "sort_rule_title",
            "sort_rule_items_count",
            "sort_rule_shuffle"
        )
    )

    init {
        viewModelScope.launch {
            sorter.setConfig(SortConfig(hideGroup = true))
        }
    }

    val albums = stateFlow()
        .distinctUntilChangedBy { it.distinctKey }
        .flatMapLatest { it.getAlbumsFlow() }
        .doSortState(sorter, viewModelScope)
    val state = stateFlow()
        .toState(AlbumsState(), viewModelScope)

    override fun intent(intent: AlbumsAction) = viewModelScope.launch {
        when (intent) {
            AlbumsAction.ToggleSearcherPanel -> reduce {
                it.copy(showSearcherPanel = !it.showSearcherPanel)
            }

            AlbumsAction.ToggleSortPanel -> reduce {
                it.copy(showSortPanel = !it.showSortPanel)
            }

            AlbumsAction.ToggleShowText -> reduce { it.copy(showText = !it.showText) }
            AlbumsAction.HideSortPanel -> reduce { it.copy(showSortPanel = false) }
            AlbumsAction.HideSearcherPanel -> reduce { it.copy(showSearcherPanel = false) }
            is AlbumsAction.SearchFor -> reduce { it.copy(searchKeyWord = intent.keyword) }
            is AlbumsAction.SelectSortAction -> sorter.setAction(intent.action)
            is AlbumsAction.UpdateSortConfig -> sorter.setConfig(intent.config)
        }
    }
}
