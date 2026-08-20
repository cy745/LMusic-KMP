package com.lalilu.lsearch.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.lalilu.MviWithIntent
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.usecase.SearchAlbumsUseCase
import com.lalilu.lmedia.domain.usecase.SearchArtistsUseCase
import com.lalilu.lmedia.domain.usecase.SearchAudiosUseCase
import com.lalilu.mviImplWithIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

/**
 * ViewModel for [com.lalilu.lsearch.screen.SearchScreen].
 *
 * Aggregates three independent UseCases — one per content type — that share a
 * single keyword. Each UseCase is re-driven whenever [SearchState.keyword]
 * changes. The active type tab is NOT held here: it lives in the UI's
 * [androidx.compose.foundation.pager.PagerState] so every page keeps its own
 * scroll position.
 *
 * All three flows run unconditionally — keyword filtering is cheap when the
 * list is small (local media library) and skipping unnecessary types would
 * couple the VM to UI concerns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Factory
class SearchVM(
    private val searchAudiosUseCase: SearchAudiosUseCase,
    private val searchAlbumsUseCase: SearchAlbumsUseCase,
    private val searchArtistsUseCase: SearchArtistsUseCase,
) : ViewModel(),
    MviWithIntent<SearchState, SearchEvent, SearchAction>
    by mviImplWithIntent(SearchState()) {

    /** Song results, reactively filtered by the current keyword. */
    val audios: Flow<List<LAudio>> = stateFlow()
        .distinctUntilChangedBy { it.keyword }
        .flatMapLatest { searchAudiosUseCase(keywords = splitKeywords(it.keyword)) }

    /** Album results, reactively filtered by the current keyword. */
    val albums: Flow<List<LAlbum>> = stateFlow()
        .distinctUntilChangedBy { it.keyword }
        .flatMapLatest { searchAlbumsUseCase(keywords = splitKeywords(it.keyword)) }

    /** Artist results, reactively filtered by the current keyword. */
    val artists: Flow<List<LArtist>> = stateFlow()
        .distinctUntilChangedBy { it.keyword }
        .flatMapLatest { searchArtistsUseCase(keywords = splitKeywords(it.keyword)) }

    val state = stateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SearchState()
        )

    override fun intent(intent: SearchAction) = viewModelScope.launch {
        when (intent) {
            is SearchAction.UpdateKeyword -> reduce { it.copy(keyword = intent.keyword) }
            SearchAction.ClearKeyword -> reduce { it.copy(keyword = "") }
        }
    }

    private fun splitKeywords(raw: String): List<String> = when {
        raw.isBlank() -> emptyList()
        raw.contains(' ') -> raw.split(' ').filter { it.isNotBlank() }
        else -> listOf(raw)
    }
}