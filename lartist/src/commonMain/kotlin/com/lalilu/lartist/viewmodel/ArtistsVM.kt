package com.lalilu.lartist.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.MviWithIntent
import com.lalilu.common.ext.requestFor
import com.lalilu.extensions.toState
import com.lalilu.lmedia.domain.repository.ArtistRepository
import com.lalilu.lmedia.domain.model.LArtist
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
    val showSearcherPanel: Boolean = false,
    val showSortPanel: Boolean = false,

    val searchKeyWord: String = "",
) {
    val distinctKey: Int = searchKeyWord.hashCode()

    fun getArtistsFlow(artistRepository: ArtistRepository): Flow<List<LArtist>> {
        val source = artistRepository.getArtists()
            .mapLatest { list -> list.map { it } }

        val keywords: List<String> = when {
            searchKeyWord.isBlank() -> emptyList()
            searchKeyWord.contains(' ') -> searchKeyWord.split(' ')
            else -> listOf(searchKeyWord)
        }

        return source.mapLatest { flow ->
            flow.filter { item -> keywords.all { "${item.title} ${item.subtitle}".contains(it, ignoreCase = true) } }
        }
    }
}

sealed interface ArtistsAction {
    data object ToggleSortPanel : ArtistsAction
    data object ToggleSearcherPanel : ArtistsAction

    data object HideSortPanel : ArtistsAction
    data object HideSearcherPanel : ArtistsAction

    data class SearchFor(val keyword: String) : ArtistsAction
    data class SelectSortAction(val action: SortAction) : ArtistsAction
    data class UpdateSortConfig(val config: SortConfig) : ArtistsAction
}

sealed interface ArtistsEvent

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class ArtistsVM(
    private val artistRepository: ArtistRepository,
    initialKeyword: String,
) : ViewModel(),
    MviWithIntent<ArtistsState, ArtistsEvent, ArtistsAction>
    by mviImplWithIntent(ArtistsState(searchKeyWord = initialKeyword)) {

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
        .flatMapLatest { it.getArtistsFlow(artistRepository) }
        .doSortState(sorter, viewModelScope)
    val state = stateFlow()
        .toState(ArtistsState(searchKeyWord = initialKeyword), viewModelScope)

    override fun intent(intent: ArtistsAction) = viewModelScope.launch {
        when (intent) {
            ArtistsAction.ToggleSearcherPanel -> reduce {
                it.copy(showSearcherPanel = !it.showSearcherPanel)
            }

            ArtistsAction.ToggleSortPanel -> reduce {
                it.copy(showSortPanel = !it.showSortPanel)
            }

            ArtistsAction.HideSortPanel -> reduce { it.copy(showSortPanel = false) }
            ArtistsAction.HideSearcherPanel -> reduce { it.copy(showSearcherPanel = false) }
            is ArtistsAction.SearchFor -> reduce { it.copy(searchKeyWord = intent.keyword) }
            is ArtistsAction.SelectSortAction -> sorter.setAction(intent.action)
            is ArtistsAction.UpdateSortConfig -> sorter.setConfig(intent.config)
        }
    }
}
