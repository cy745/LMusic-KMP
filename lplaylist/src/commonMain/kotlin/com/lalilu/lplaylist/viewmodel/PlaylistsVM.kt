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

package com.lalilu.lplaylist.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.MviWithIntent
import com.lalilu.extensions.ItemSelector
import com.lalilu.extensions.toState
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.repository.PlaylistRepository
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
data class PlaylistsState(
    // control flags
    val showSearcherPanel: Boolean = false,

    // control params
    val searchKeyWord: String = "",
) {
    val distinctKey: Int = searchKeyWord.hashCode()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPlaylistsFlow(playlistRepo: PlaylistRepository): Flow<List<LPlaylist>> {
        val sources = playlistRepo.getPlaylistsFlow()

        val keywords: List<String> = when {
            searchKeyWord.isBlank() -> emptyList()
            searchKeyWord.contains(' ') -> searchKeyWord.split(' ')
            else -> listOf(searchKeyWord)
        }

        val searchResult = sources.mapLatest { flow ->
            flow.filter { item -> keywords.all { item.getMatchText().contains(it) } }
        }

        return searchResult
    }
}

sealed interface PlaylistsAction {
    data class UpdatePlaylist(val playlists: List<LPlaylist>) : PlaylistsAction
    data class TryRemovePlaylist(val playlists: Collection<LPlaylist>) : PlaylistsAction
    data class SearchFor(val keyword: String) : PlaylistsAction
    data object HideSearcherPanel : PlaylistsAction
    data object ShowSearcherPanel : PlaylistsAction
}

sealed interface PlaylistsEvent {

}

@KoinViewModel
class PlaylistsVM(private val playlistRepo: PlaylistRepository) : ViewModel(),
    MviWithIntent<PlaylistsState, PlaylistsEvent, PlaylistsAction>
    by mviImplWithIntent(PlaylistsState()) {

    val selector = ItemSelector<LPlaylist>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlists = stateFlow()
        .distinctUntilChangedBy { it.distinctKey }
        .flatMapLatest { it.getPlaylistsFlow(playlistRepo) }
        .toState(emptyList(), viewModelScope)

    val state = stateFlow().toState(PlaylistsState(), viewModelScope)

    override fun intent(intent: PlaylistsAction) = viewModelScope.launch {
        when (intent) {
            is PlaylistsAction.UpdatePlaylist -> {
                playlistRepo.setPlaylists(intent.playlists)
            }

            is PlaylistsAction.TryRemovePlaylist -> {
                playlistRepo.removeByIds(intent.playlists.map { it.id })
            }

            is PlaylistsAction.SearchFor -> {
                reduce { it.copy(searchKeyWord = intent.keyword) }
            }

            is PlaylistsAction.HideSearcherPanel -> {
                reduce { it.copy(showSearcherPanel = false) }
            }

            is PlaylistsAction.ShowSearcherPanel -> {
                reduce { it.copy(showSearcherPanel = true) }
            }

            else -> {}
        }
    }
}