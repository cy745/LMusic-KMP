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
import co.touchlab.kermit.Logger
import com.lalilu.MviWithIntent
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.ItemRecorder
import com.lalilu.extensions.ItemSelector
import com.lalilu.extensions.toState
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref
import com.lalilu.lmedia.sortable.*
import com.lalilu.lplayer.LPlayer
import com.lalilu.mviImplWithIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@Stable
@Immutable
data class ArtistDetailState(
    val artistId: String,

    // control flags
    val showSortPanel: Boolean = false,
    val showJumperDialog: Boolean = false,
    val showSearcherPanel: Boolean = false,

    // control params
    val searchKeyWord: String = "",
) {
    val distinctKey: Int = searchKeyWord.hashCode()

    fun getArtistFlow(): Flow<LArtist?> {
        return LMedia.instance.flow<LArtist>(artistId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getSongsFlow(): Flow<List<LAudio>> {
        val source: Flow<List<LAudio>> = getArtistFlow()
            .mapLatest { artist: LArtist? -> artist?.ref<LAudio>()?.toList() ?: emptyList() }

        val keywords: List<String> = when {
            searchKeyWord.isBlank() -> emptyList()
            searchKeyWord.contains(' ') -> searchKeyWord.split(' ')
            else -> listOf(searchKeyWord)
        }

        return source.mapLatest { flow: List<LAudio> ->
            flow.filter { item: LAudio -> keywords.all { item.getMatchText().contains(it) } }
        }
    }
}

sealed interface ArtistDetailEvent {
    data class ScrollToItem(val key: Any) : ArtistDetailEvent
}

sealed interface ArtistDetailAction {
    data object ToggleSortPanel : ArtistDetailAction
    data object ToggleSearcherPanel : ArtistDetailAction
    data object ToggleJumperDialog : ArtistDetailAction

    data object HideSortPanel : ArtistDetailAction
    data object HideSearcherPanel : ArtistDetailAction
    data object HideJumperDialog : ArtistDetailAction

    data object LocaleToPlayingItem : ArtistDetailAction
    data class LocaleToGroupItem(val item: GroupId) : ArtistDetailAction
    data class SearchFor(val keyword: String) : ArtistDetailAction
    data class SelectSortAction(val action: SortAction) : ArtistDetailAction
    data class UpdateSortConfig(val config: SortConfig) : ArtistDetailAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class ArtistDetailVM(
    private val artistId: String
) : ViewModel(),
    MviWithIntent<ArtistDetailState, ArtistDetailEvent, ArtistDetailAction> by
    mviImplWithIntent(ArtistDetailState(artistId)) {
    companion object {
        private const val TAG = "ArtistDetailVM"
    }

    val selector = ItemSelector<LAudio>()
    val recorder = ItemRecorder()
    val sorter = SortManager(
        prefix = "artist_detail_",
        supportedActions = requestFor<SortAction>(
            "sort_rule_normal",
            "sort_rule_album",
            "sort_rule_title",
            "sort_rule_add_time",
            "sort_rule_duration",
            "sort_rule_shuffle",
            "sort_rule_play_count",
            "sort_rule_last_play_time"
        )
    )

    val songs = stateFlow()
        .distinctUntilChangedBy { it.distinctKey }
        .flatMapLatest { it.getSongsFlow() }
        .doSortState(sorter, viewModelScope)
    val state = stateFlow()
        .toState(ArtistDetailState(artistId), viewModelScope)

    override fun intent(intent: ArtistDetailAction) = viewModelScope.launch {
        when (intent) {
            ArtistDetailAction.ToggleJumperDialog -> reduce {
                it.copy(showJumperDialog = !it.showJumperDialog)
            }

            ArtistDetailAction.ToggleSearcherPanel -> reduce {
                it.copy(showSearcherPanel = !it.showSearcherPanel)
            }

            ArtistDetailAction.ToggleSortPanel -> reduce {
                it.copy(showSortPanel = !it.showSortPanel)
            }

            ArtistDetailAction.HideSortPanel -> reduce { it.copy(showSortPanel = false) }
            ArtistDetailAction.HideSearcherPanel -> reduce { it.copy(showSearcherPanel = false) }
            ArtistDetailAction.HideJumperDialog -> reduce { it.copy(showJumperDialog = false) }
            is ArtistDetailAction.SearchFor -> reduce { it.copy(searchKeyWord = intent.keyword) }
            is ArtistDetailAction.SelectSortAction -> sorter.setAction(intent.action)
            is ArtistDetailAction.UpdateSortConfig -> sorter.setConfig(intent.config)
            is ArtistDetailAction.LocaleToGroupItem -> postEvent {
                ArtistDetailEvent.ScrollToItem(intent.item)
            }

            is ArtistDetailAction.LocaleToPlayingItem -> {
                val mediaId = LPlayer.instance.queue.currentItem()?.idValue() ?: run {
                    Logger.e(tag = TAG, messageString = "can not find playing item's mediaId")
                    return@launch
                }
                postEvent { ArtistDetailEvent.ScrollToItem(mediaId) }
            }

            else -> {
                Logger.i(tag = TAG, messageString = "Not implemented action: $intent")
            }
        }
    }
}
