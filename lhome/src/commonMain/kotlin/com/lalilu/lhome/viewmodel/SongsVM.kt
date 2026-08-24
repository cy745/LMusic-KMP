package com.lalilu.lhome.viewmodel

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
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.usecase.SearchAudiosUseCase
import com.lalilu.lmedia.sortable.*
import com.lalilu.lplayer.LPlayer
import com.lalilu.mviImplWithIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory


@Stable
@Immutable
data class SongsState(
    val mediaIds: List<String> = emptyList(),

    val showSortPanel: Boolean = false,
    val showJumperDialog: Boolean = false,
    val showSearcherPanel: Boolean = false,

    val searchKeyWord: String = ""
) {
    val distinctKey: Int = mediaIds.hashCode() + searchKeyWord.hashCode()
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
    data class LocaleToGroupItem(val item: GroupId) : SongsAction
    data class SearchFor(val keyword: String) : SongsAction
    data class SelectSortAction(val action: SortAction) : SongsAction
    data class UpdateSortConfig(val config: SortConfig) : SongsAction
}

@Factory
@OptIn(ExperimentalCoroutinesApi::class)
class SongsVM(
    private val mediaIds: List<String>,
    private val audioRepository: AudioRepository,
    private val searchAudiosUseCase: SearchAudiosUseCase,
    initialKeyword: String,
) : ViewModel(), MviWithIntent<SongsState, SongsEvent, SongsAction> by mviImplWithIntent(
    SongsState(mediaIds = mediaIds, searchKeyWord = initialKeyword)
) {
    val recorder = ItemRecorder()
    val selector = ItemSelector<LAudio>()
    val sorter = SortManager(
        prefix = "songs_",
        supportedActions = requestFor<SortAction>(
            "sort_rule_normal",
            "sort_rule_title",
            "sort_rule_add_time",
            "sort_rule_duration",
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
            searchAudiosUseCase(
                ids = state.mediaIds.ifEmpty { null },
                keywords = keywords
            )
        }
        .map { list -> list.map { it } }
        .doSortState(sorter, viewModelScope)
    val state = stateFlow().toState(
        SongsState(mediaIds = mediaIds, searchKeyWord = initialKeyword),
        viewModelScope
    )

    override fun intent(intent: SongsAction) = viewModelScope.launch {
        when (intent) {
            SongsAction.ToggleJumperDialog -> reduce { it.copy(showJumperDialog = !it.showJumperDialog) }
            SongsAction.ToggleSearcherPanel -> reduce { it.copy(showSearcherPanel = !it.showSearcherPanel) }
            SongsAction.ToggleSortPanel -> reduce { it.copy(showSortPanel = !it.showSortPanel) }
            SongsAction.HideSortPanel -> reduce { it.copy(showSortPanel = false) }
            SongsAction.HideSearcherPanel -> reduce { it.copy(showSearcherPanel = false) }
            SongsAction.HideJumperDialog -> reduce { it.copy(showJumperDialog = false) }
            is SongsAction.SearchFor -> reduce { it.copy(searchKeyWord = intent.keyword) }
            is SongsAction.SelectSortAction -> sorter.setAction(intent.action)
            is SongsAction.UpdateSortConfig -> sorter.setConfig(intent.config)
            is SongsAction.LocaleToGroupItem -> postEvent { SongsEvent.ScrollToItem(intent.item) }
            is SongsAction.LocaleToPlayingItem -> {
                val mediaId = LPlayer.instance.queue.currentItem()?.id ?: run {
                    Logger.e("can not find playing item's mediaId")
                    return@launch
                }
                postEvent { SongsEvent.ScrollToItem(mediaId) }
            }

            else -> {
                Logger.w("Not implemented action: $intent")
            }
        }
    }
}
