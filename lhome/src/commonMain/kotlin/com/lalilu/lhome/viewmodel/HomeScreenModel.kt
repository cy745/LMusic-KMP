package com.lalilu.lhome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.lhome.LHomeKV
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.AlbumRepository
import com.lalilu.lmedia.domain.repository.ArtistRepository
import com.lalilu.lmedia.domain.usecase.GetDailyRecommendsUseCase
import com.lalilu.lmedia.entity.*
import com.lalilu.lmedia.entity.toLegacyAudio
import com.lalilu.lmedia.entity.toLegacyLItem
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

    val recentlyAdded: StateFlow<List<LAudio>> = audioRepository.getAudios()
        .mapLatest { list -> list.map { it.toLegacyAudio() } }
        .mapLatest { it.take(15) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    val dailyRecommends: StateFlow<List<LItem>> = dailyRecommendsUseCase.get()
        .mapLatest { list -> list.map { it.toLegacyLItem() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    init {
        dailyRecommendsUseCase.needsRefresh()
            .onEach { needRefresh -> if (needRefresh) requireUpdateDailyRecommends() }
            .launchIn(viewModelScope)
    }

    fun requireUpdateDailyRecommends() = viewModelScope.launch {
        dailyRecommendsUseCase.refresh()
    }
}
