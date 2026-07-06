package com.lalilu.lalbum.viewmodel

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
import com.lalilu.lmedia.domain.repository.AlbumRepository
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.usecase.SearchAudiosUseCase
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.toLegacyAlbum
import com.lalilu.lmedia.entity.toLegacyAudio
import com.lalilu.lmedia.sortable.*
import com.lalilu.lplayer.LPlayer
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
data class AlbumDetailState(
    val albumId: String,

    val showSortPanel: Boolean = false,
    val showJumperDialog: Boolean = false,
    val showSearcherPanel: Boolean = false,

    val searchKeyWord: String = "",
) {
    val distinctKey: Int = searchKeyWord.hashCode()

    fun getAlbumFlow(albumRepository: AlbumRepository): Flow<LAlbum?> {
        return albumRepository.getAlbum(albumId)
            .mapLatest { it?.toLegacyAlbum() }
    }

    fun getSongsFlow(searchAudiosUseCase: SearchAudiosUseCase): Flow<List<LAudio>> {
        return searchAudiosUseCase(ids = null, keywords = emptyList())
            .map { list -> list.map { it.toLegacyAudio() } }
    }
}

sealed interface AlbumDetailEvent {
    data class ScrollToItem(val key: Any) : AlbumDetailEvent
}

sealed interface AlbumDetailAction {
    data object ToggleSortPanel : AlbumDetailAction
    data object ToggleSearcherPanel : AlbumDetailAction
    data object ToggleJumperDialog : AlbumDetailAction

    data object HideSortPanel : AlbumDetailAction
    data object HideSearcherPanel : AlbumDetailAction
    data object HideJumperDialog : AlbumDetailAction

    data object LocaleToPlayingItem : AlbumDetailAction
    data class LocaleToGroupItem(val item: GroupId) : AlbumDetailAction
    data class SearchFor(val keyword: String) : AlbumDetailAction
    data class SelectSortAction(val action: SortAction) : AlbumDetailAction
    data class UpdateSortConfig(val config: SortConfig) : AlbumDetailAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class AlbumDetailVM(
    private val albumId: String,
    private val album: LAlbum? = null,
    private val albumRepository: AlbumRepository,
    private val audioRepository: AudioRepository,
    private val searchAudiosUseCase: SearchAudiosUseCase,
) : ViewModel(),
    MviWithIntent<AlbumDetailState, AlbumDetailEvent, AlbumDetailAction> by
    mviImplWithIntent(AlbumDetailState(albumId)) {
    companion object {
        private const val TAG = "AlbumDetailVM"
    }

    val selector = ItemSelector<LAudio>()
    val recorder = ItemRecorder()
    val sorter = SortManager(
        prefix = "album_detail_",
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

    val albumFlow = stateFlow()
        .flatMapLatest { it.getAlbumFlow(albumRepository) }
        .toState(viewModelScope)

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
        .map { list -> list.map { it.toLegacyAudio() } }
        .doSortState(sorter, viewModelScope)
    val state = stateFlow()
        .toState(AlbumDetailState(albumId), viewModelScope)

    override fun intent(intent: AlbumDetailAction) = viewModelScope.launch {
        when (intent) {
            AlbumDetailAction.ToggleJumperDialog -> reduce {
                it.copy(showJumperDialog = !it.showJumperDialog)
            }

            AlbumDetailAction.ToggleSearcherPanel -> reduce {
                it.copy(showSearcherPanel = !it.showSearcherPanel)
            }

            AlbumDetailAction.ToggleSortPanel -> reduce {
                it.copy(showSortPanel = !it.showSortPanel)
            }

            AlbumDetailAction.HideSortPanel -> reduce { it.copy(showSortPanel = false) }
            AlbumDetailAction.HideSearcherPanel -> reduce { it.copy(showSearcherPanel = false) }
            AlbumDetailAction.HideJumperDialog -> reduce { it.copy(showJumperDialog = false) }
            is AlbumDetailAction.SearchFor -> reduce { it.copy(searchKeyWord = intent.keyword) }
            is AlbumDetailAction.SelectSortAction -> sorter.setAction(intent.action)
            is AlbumDetailAction.UpdateSortConfig -> sorter.setConfig(intent.config)
            is AlbumDetailAction.LocaleToGroupItem -> postEvent {
                AlbumDetailEvent.ScrollToItem(intent.item)
            }

            is AlbumDetailAction.LocaleToPlayingItem -> {
                val mediaId = LPlayer.instance.queue.currentItem()?.idValue() ?: run {
                    Logger.e(tag = TAG, messageString = "can not find playing item's mediaId")
                    return@launch
                }
                postEvent { AlbumDetailEvent.ScrollToItem(mediaId) }
            }

            else -> {
                Logger.i(tag = TAG, messageString = "Not implemented action: $intent")
            }
        }
    }
}
