package com.lalilu.lhome.viewmodel

import com.lalilu.extensions.toQueryState

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.lhome.LHomeKV
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.AlbumRepository
import com.lalilu.lmedia.domain.repository.ArtistRepository
import com.lalilu.lmedia.domain.usecase.GetDailyRecommendsUseCase
import com.lalilu.lmedia.domain.usecase.RecommendItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenModel(
    private val audioRepository: AudioRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val dailyRecommendsUseCase: GetDailyRecommendsUseCase,
    lHomeKV: LHomeKV
) : ViewModel() {

    val recentlyAdded = audioRepository.getAudios()
        .mapLatest { it.take(15) }
        .toQueryState(emptyList(), viewModelScope)

    val dailyRecommends = dailyRecommendsUseCase.get()
        .toQueryState(emptyList(), viewModelScope)

    init {
        dailyRecommendsUseCase.needsRefresh()
            .onEach { needRefresh -> if (needRefresh) requireUpdateDailyRecommends() }
            .launchIn(viewModelScope)
    }

    fun requireUpdateDailyRecommends() = viewModelScope.launch {
        dailyRecommendsUseCase.refresh()
    }
}
