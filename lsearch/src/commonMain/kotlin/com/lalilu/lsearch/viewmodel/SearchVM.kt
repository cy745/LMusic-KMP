package com.lalilu.lsearch.viewmodel

import com.lalilu.extensions.queryState

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
import kotlinx.coroutines.flow.combine
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
 * 三类结果由 ViewModel 按关键词统一查询并持有最后成功快照。这样页面跳转后返回时可以立即拿到上一次结果，
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

    /** 当前导航栈页面实例的推荐词快照；页面出栈并销毁本 ViewModel 时一并清除。 */
    internal val recommendationState = SearchRecommendationState()

    /**
     * 空关键词页面使用的全量名称候选池。三类仓库任意一类更新时都会重新整理候选名称；
     * 随机抽取、定时替换和动画只在推荐组件实际进入组合时运行。
     */
    val recommendationCandidates: StateFlow<SearchRecommendationCandidates> = combine(
        searchArtistsUseCase(),
        searchAlbumsUseCase(),
        searchAudiosUseCase(),
    ) { artists, albums, audios ->
        SearchRecommendationCandidates.create(
            artistNames = artists.map { it.title },
            albumNames = albums.map { it.title },
            audioNames = audios.map { it.title },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = SearchRecommendationCandidates.Empty,
    )

    data class Results(val audios: List<LAudio> = emptyList(), val albums: List<LAlbum> = emptyList(),
        val artists: List<LArtist> = emptyList())

    // Publish all three result types together for the same keyword.
    val results = stateFlow().distinctUntilChangedBy { it.keyword }
        .queryState(Results(), viewModelScope) { state ->
            if (state.keyword.isBlank()) flowOf(Results())
            else combine(
                searchAudiosUseCase(keywords = splitKeywords(state.keyword)),
                searchAlbumsUseCase(keywords = splitKeywords(state.keyword)),
                searchArtistsUseCase(keywords = splitKeywords(state.keyword)),
            ) { audios, albums, artists -> Results(audios, albums, artists) }
        }

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
