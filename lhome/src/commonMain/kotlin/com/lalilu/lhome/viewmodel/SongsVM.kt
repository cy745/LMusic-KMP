package com.lalilu.lhome.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.extensions.toState
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
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
    val searchKeyWord: String = ""
)

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
) : ViewModel() {

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

    fun intent(action: SongsAction) {
        viewModelScope.launch {
            val currentState = _state.value
            val newState = when (action) {
                SongsAction.ToggleJumperDialog -> currentState.copy(showJumperDialog = !currentState.showJumperDialog)
                SongsAction.ToggleSearcherPanel -> currentState.copy(showSearcherPanel = !currentState.showSearcherPanel)
                SongsAction.ToggleSortPanel -> currentState.copy(showSortPanel = !currentState.showSortPanel)
                SongsAction.HideSortPanel -> currentState.copy(showSortPanel = false)
                SongsAction.HideSearcherPanel -> currentState.copy(showSearcherPanel = false)
                SongsAction.HideJumperDialog -> currentState.copy(showJumperDialog = false)
                is SongsAction.SearchFor -> currentState.copy(searchKeyWord = action.keyword)
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
