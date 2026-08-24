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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

/**
 * ViewModel for [com.lalilu.lsearch.screen.SearchScreen].
 *
 * Aggregates three independent UseCases — one per content type — that share a
 * single keyword. Each UseCase is re-driven whenever [SearchState.keyword]
 * changes. The UI always renders limited previews of all three result types.
 *
 * 三类结果都由 ViewModel 持有为热 StateFlow。这样页面跳转后返回时可以立即拿到上一次结果，
 * 避免 LazyGrid 在冷 Flow 重新发射前短暂收到空列表、进而把已经恢复的滚动位置钳制到顶部。
 * 空关键词不会触发仓库搜索，也不会向 UI 暴露全量媒体数据。
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
    val audios: StateFlow<List<LAudio>> = stateFlow()
        .distinctUntilChangedBy { it.keyword }
        .flatMapLatest {
            if (it.keyword.isBlank()) flowOf(emptyList())
            else searchAudiosUseCase(keywords = splitKeywords(it.keyword))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Album results, reactively filtered by the current keyword. */
    val albums: StateFlow<List<LAlbum>> = stateFlow()
        .distinctUntilChangedBy { it.keyword }
        .flatMapLatest {
            if (it.keyword.isBlank()) flowOf(emptyList())
            else searchAlbumsUseCase(keywords = splitKeywords(it.keyword))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Artist results, reactively filtered by the current keyword. */
    val artists: StateFlow<List<LArtist>> = stateFlow()
        .distinctUntilChangedBy { it.keyword }
        .flatMapLatest {
            if (it.keyword.isBlank()) flowOf(emptyList())
            else searchArtistsUseCase(keywords = splitKeywords(it.keyword))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
