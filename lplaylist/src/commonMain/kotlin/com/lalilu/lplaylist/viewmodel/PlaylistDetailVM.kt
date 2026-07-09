package com.lalilu.lplaylist.viewmodel

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
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.usecase.SearchAudiosUseCase
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.sortable.*
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplaylist.entity.LPlaylist
import com.lalilu.lplaylist.repository.PlaylistRepository
import com.lalilu.mviImplWithIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@Stable
@Immutable
data class PlaylistDetailState(
    val playlistId: String,

    val showSortPanel: Boolean = false,
    val showJumperDialog: Boolean = false,
    val showSearcherPanel: Boolean = false,

    val searchKeyWord: String = "",
) {
    val distinctKey: Int = searchKeyWord.hashCode()

    fun getPlaylistFlow(playlistRepo: PlaylistRepository): Flow<LPlaylist?> {
        return playlistRepo.getPlaylistsFlow()
            .mapLatest { list -> list.firstOrNull { it.id == playlistId } }
    }

    fun getSongsFlow(
        searchAudiosUseCase: SearchAudiosUseCase
    ): Flow<List<LAudio>> {
        // Note: using SearchAudiosUseCase for filtered query
        // Playlist audio IDs should be matched via the use case
        return searchAudiosUseCase(ids = null, keywords = emptyList())
            .map { list -> list.map { it } }
    }
}

sealed interface PlaylistDetailEvent {
    data class ScrollToItem(val key: Any) : PlaylistDetailEvent
}

sealed interface PlaylistDetailAction {
    data object ToggleSortPanel : PlaylistDetailAction
    data object ToggleSearcherPanel : PlaylistDetailAction
    data object ToggleJumperDialog : PlaylistDetailAction

    data object HideSortPanel : PlaylistDetailAction
    data object HideSearcherPanel : PlaylistDetailAction
    data object HideJumperDialog : PlaylistDetailAction

    data object LocaleToPlayingItem : PlaylistDetailAction
    data class LocaleToGroupItem(val item: GroupId) : PlaylistDetailAction
    data class SearchFor(val keyword: String) : PlaylistDetailAction
    data class SelectSortAction(val action: SortAction) : PlaylistDetailAction
    data class UpdateSortConfig(val config: SortConfig) : PlaylistDetailAction
    data class UpdatePlaylist(val mediaIds: List<String>) : PlaylistDetailAction
    data class RemoveItems(val mediaIds: List<String>) : PlaylistDetailAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class PlaylistDetailVM(
    private val playlistId: String,
    private val playlistRepo: PlaylistRepository,
    private val audioRepository: AudioRepository,
    private val searchAudiosUseCase: SearchAudiosUseCase,
) : ViewModel(),
    MviWithIntent<PlaylistDetailState, PlaylistDetailEvent, PlaylistDetailAction> by
    mviImplWithIntent(PlaylistDetailState(playlistId)) {
    companion object {
        private const val TAG = "PlaylistDetailVM"
    }

    val selector = ItemSelector<LAudio>()
    val recorder = ItemRecorder()
    val sorter = SortManager(
        prefix = "playlist_detail_",
        supportedActions = requestFor<SortAction>(
            "sort_rule_normal",
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
        .flatMapLatest { state ->
            val keywords = when {
                state.searchKeyWord.isBlank() -> emptyList()
                state.searchKeyWord.contains(' ') -> state.searchKeyWord.split(' ')
                else -> listOf(state.searchKeyWord)
            }
            searchAudiosUseCase(ids = null, keywords = keywords)
        }
        .map { list -> list.map { it } }
        .doSortState(sorter, viewModelScope)
    val playlist = stateFlow()
        .flatMapLatest { it.getPlaylistFlow(playlistRepo) }
        .toState(viewModelScope)
    val state = stateFlow()
        .toState(PlaylistDetailState(playlistId), viewModelScope)

    override fun intent(intent: PlaylistDetailAction) = viewModelScope.launch {
        when (intent) {
            PlaylistDetailAction.ToggleJumperDialog -> reduce { it.copy(showJumperDialog = !it.showJumperDialog) }
            PlaylistDetailAction.ToggleSearcherPanel -> reduce { it.copy(showSearcherPanel = !it.showSearcherPanel) }
            PlaylistDetailAction.ToggleSortPanel -> reduce { it.copy(showSortPanel = !it.showSortPanel) }
            PlaylistDetailAction.HideSortPanel -> reduce { it.copy(showSortPanel = false) }
            PlaylistDetailAction.HideSearcherPanel -> reduce { it.copy(showSearcherPanel = false) }
            PlaylistDetailAction.HideJumperDialog -> reduce { it.copy(showJumperDialog = false) }
            is PlaylistDetailAction.SearchFor -> reduce { it.copy(searchKeyWord = intent.keyword) }
            is PlaylistDetailAction.SelectSortAction -> sorter.setAction(intent.action)
            is PlaylistDetailAction.UpdateSortConfig -> sorter.setConfig(intent.config)
            is PlaylistDetailAction.LocaleToGroupItem -> postEvent {
                PlaylistDetailEvent.ScrollToItem(intent.item)
            }

            is PlaylistDetailAction.LocaleToPlayingItem -> {
                val mediaId = LPlayer.instance.queue.currentItem()?.id ?: run {
                    Logger.e(tag = TAG, messageString = "can not find playing item's mediaId")
                    return@launch
                }
                postEvent { PlaylistDetailEvent.ScrollToItem(mediaId) }
            }

            is PlaylistDetailAction.UpdatePlaylist -> {
                playlist.value?.copy(mediaIds = intent.mediaIds)
                    ?.let { playlistRepo.save(it) }
            }

            is PlaylistDetailAction.RemoveItems -> {
                playlistRepo.removeMediaIdsFromPlaylist(
                    mediaIds = intent.mediaIds,
                    playlistId = playlistId
                )
            }

            else -> {
                Logger.i(tag = TAG, messageString = "Not implemented action: $intent")
            }
        }
    }
}
