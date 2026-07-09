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
import com.lalilu.lmedia.domain.repository.ArtistRepository
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.usecase.GetRelatedArtistsUseCase
import com.lalilu.lmedia.domain.usecase.SearchAudiosUseCase
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
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
data class ArtistDetailState(
    val artistId: String,

    val showSortPanel: Boolean = false,
    val showJumperDialog: Boolean = false,
    val showSearcherPanel: Boolean = false,

    val searchKeyWord: String = "",

    val relatedArtists: List<LArtist> = emptyList()
) {
    val distinctKey: Int = searchKeyWord.hashCode()

    fun getArtistFlow(artistRepository: ArtistRepository): Flow<LArtist?> {
        return artistRepository.getArtist(artistId)
            .mapLatest { it }
    }

    fun getSongsFlow(artistRepository: ArtistRepository): Flow<List<LAudio>> {
        return artistRepository.getAudiosByArtist(artistId)
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
    private val artistId: String,
    private val artistRepository: ArtistRepository,
    private val audioRepository: AudioRepository,
    private val searchAudiosUseCase: SearchAudiosUseCase,
    private val getRelatedArtistsUseCase: GetRelatedArtistsUseCase
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

    val songs = artistRepository.getAudiosByArtist(artistId)
        .flatMapLatest { audios ->
            stateFlow().distinctUntilChangedBy { it.distinctKey }
                .map { state ->
                    if (state.searchKeyWord.isBlank()) return@map audios
                    val keywords = if (state.searchKeyWord.contains(' '))
                        state.searchKeyWord.split(' ') else listOf(state.searchKeyWord)
                    audios.filter { audio ->
                        keywords.all { "${audio.title}_${audio.subtitle}".contains(it, ignoreCase = true) }
                    }
                }
        }
        .doSortState(sorter, viewModelScope)
    val state = stateFlow()
        .toState(ArtistDetailState(artistId), viewModelScope)

    init {
        viewModelScope.launch {
            loadRelatedArtists()
        }
    }

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
                val mediaId = LPlayer.instance.queue.currentItem()?.id ?: run {
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

    fun loadRelatedArtists() {
        viewModelScope.launch {
            val related = getRelatedArtistsUseCase(artistId)
            val legacyRelated = related.map { it }
            reduce { it.copy(relatedArtists = legacyRelated) }
        }
    }
}
