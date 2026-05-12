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

package com.lalilu.lartist.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.MviWithIntent
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.toState
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LArtist
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
data class ArtistsState(
    // control flags
    val showSearcherPanel: Boolean = false,
    val showSortPanel: Boolean = false,

    // control params
    val searchKeyWord: String = "",
    val showText: Boolean = true,
) {
    val distinctKey: Int = searchKeyWord.hashCode()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getArtistsFlow(): Flow<List<LArtist>> {
        val source = LMedia.instance.flow<LArtist>()

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

sealed interface ArtistsAction {
    data object ToggleSortPanel : ArtistsAction
    data object ToggleSearcherPanel : ArtistsAction
    data object ToggleShowText : ArtistsAction

    data object HideSortPanel : ArtistsAction
    data object HideSearcherPanel : ArtistsAction

    data class SearchFor(val keyword: String) : ArtistsAction
    data class SelectSortAction(val action: SortAction) : ArtistsAction
    data class UpdateSortConfig(val config: SortConfig) : ArtistsAction
}

sealed interface ArtistsEvent

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class ArtistsVM : ViewModel(),
    MviWithIntent<ArtistsState, ArtistsEvent, ArtistsAction>
    by mviImplWithIntent(ArtistsState()) {

    val sorter = SortManager(
        prefix = "artists_",
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

    val artists = stateFlow()
        .distinctUntilChangedBy { it.distinctKey }
        .flatMapLatest { it.getArtistsFlow() }
        .doSortState(sorter, viewModelScope)
    val state = stateFlow()
        .toState(ArtistsState(), viewModelScope)

    override fun intent(intent: ArtistsAction) = viewModelScope.launch {
        when (intent) {
            ArtistsAction.ToggleSearcherPanel -> reduce {
                it.copy(showSearcherPanel = !it.showSearcherPanel)
            }

            ArtistsAction.ToggleSortPanel -> reduce {
                it.copy(showSortPanel = !it.showSortPanel)
            }

            ArtistsAction.ToggleShowText -> reduce { it.copy(showText = !it.showText) }
            ArtistsAction.HideSortPanel -> reduce { it.copy(showSortPanel = false) }
            ArtistsAction.HideSearcherPanel -> reduce { it.copy(showSearcherPanel = false) }
            is ArtistsAction.SearchFor -> reduce { it.copy(searchKeyWord = intent.keyword) }
            is ArtistsAction.SelectSortAction -> sorter.setAction(intent.action)
            is ArtistsAction.UpdateSortConfig -> sorter.setConfig(intent.config)
        }
    }
}
