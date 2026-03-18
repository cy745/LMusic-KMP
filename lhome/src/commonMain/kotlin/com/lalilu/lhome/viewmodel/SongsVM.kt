package com.lalilu.lhome.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.MviWithIntent
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.ItemRecorder
import com.lalilu.extensions.ItemSelector
import com.lalilu.extensions.toState
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.sortable.SortAction
import com.lalilu.lmedia.sortable.SortManager
import com.lalilu.mviImplWithIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory


@Stable
@Immutable
data class SongsState(
    // initialize values
    val mediaIds: List<String> = emptyList(),

    // control flags
    val showSortPanel: Boolean = false,
    val showJumperDialog: Boolean = false,
    val showSearcherPanel: Boolean = false,

    // control params
    val searchKeyWord: String = "",
    val selectedSortAction: ListAction = SortStaticAction.Normal,
) {
    val distinctKey: Int =
        mediaIds.hashCode() + searchKeyWord.hashCode() + selectedSortAction.hashCode()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getSongsFlow(): Flow<Map<GroupIdentity, List<LAudio>>> {
        val source = if (mediaIds.isEmpty()) LMedia.instance.flow<LAudio>()
        else LMedia.instance.mapByFlow<LAudio>(mediaIds)

        val keywords: List<String> = when {
            searchKeyWord.isBlank() -> emptyList()
            searchKeyWord.contains(' ') -> searchKeyWord.split(' ')
            else -> listOf(searchKeyWord)
        }

        val searchResult = source.mapLatest { flow ->
            flow.filter { item -> keywords.all { item.getMatchStr().contains(it.uppercase()) } }
        }

        return when (selectedSortAction) {
            is SortStaticAction -> searchResult.mapLatest {
                selectedSortAction.doSort(it, false)
            }

            is SortDynamicAction -> selectedSortAction.doSort(searchResult, false)
            else -> flowOf(emptyMap())
        }
    }
}

sealed interface SongsEvent {
    data class ScrollToItem(val key: Any) : SongsEvent
}

sealed interface SongsAction {
    data object ToggleSortPanel : SongsAction
    data object ToggleSearcherPanel : SongsAction
    data object ToggleJumperDialog : SongsAction

    data object HideSortPanel : SongsAction
    data object HideSearcherPanel : SongsAction
    data object HideJumperDialog : SongsAction

    data object LocaleToPlayingItem : SongsAction
    data class SearchFor(val keyword: String) : SongsAction
}

@Factory
@OptIn(ExperimentalCoroutinesApi::class)
class SongsVM(
    private val mediaIds: List<String>,
) : ViewModel(), MviWithIntent<SongsState, SongsEvent, SongsAction> by mviImplWithIntent(SongsState(mediaIds)) {
    val recorder = ItemRecorder()
    val selector = ItemSelector<LAudio>()
    val sorter = SortManager(
        prefix = "songs_",
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

    private val _state = MutableStateFlow(SongsState(mediaIds))
    val state: StateFlow<SongsState> = _state

    private val _searchState = MutableStateFlow(SongsState(mediaIds))

    val songs: State<List<LAudio>> = _searchState
        .flatMapLatest { state ->
            val source = if (state.mediaIds.isEmpty()) {
                LMedia.instance.flow<LAudio>()
            } else {
                LMedia.instance.mapByFlow(state.mediaIds)
            }

            val keywords: List<String> = when {
                state.searchKeyWord.isBlank() -> emptyList()
                state.searchKeyWord.contains(' ') -> state.searchKeyWord.split(' ')
                else -> listOf(state.searchKeyWord)
            }

            source.mapLatest { list ->
                if (keywords.isEmpty()) {
                    list
                } else {
                    list.filter { song ->
                        keywords.all { keyword ->
                            song.titleValue().contains(keyword, ignoreCase = true) ||
                                    song.subtitleValue().contains(keyword, ignoreCase = true)
                        }
                    }
                }
            }
        }
        .toState(emptyList(), viewModelScope)

    override fun intent(intent: SongsAction) {
        viewModelScope.launch {
            val currentState = _state.value
            val newState = when (intent) {
                SongsAction.ToggleJumperDialog -> currentState.copy(showJumperDialog = !currentState.showJumperDialog)
                SongsAction.ToggleSearcherPanel -> currentState.copy(showSearcherPanel = !currentState.showSearcherPanel)
                SongsAction.ToggleSortPanel -> currentState.copy(showSortPanel = !currentState.showSortPanel)
                SongsAction.HideSortPanel -> currentState.copy(showSortPanel = false)
                SongsAction.HideSearcherPanel -> currentState.copy(showSearcherPanel = false)
                SongsAction.HideJumperDialog -> currentState.copy(showJumperDialog = false)
                is SongsAction.SearchFor -> currentState.copy(searchKeyWord = intent.keyword)
                SongsAction.LocaleToPlayingItem -> {
                    // Handle scroll to playing item - can emit event if needed
                    currentState
                }
            }
            _state.value = newState
            _searchState.value = newState
        }
    }
}
