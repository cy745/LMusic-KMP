package com.lalilu.lhistory.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.lalilu.extensions.toState
import com.lalilu.lhistory.repository.HistoryRepository
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.toLegacyAudio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single
class HistoryVM(
    val historyRepo: HistoryRepository,
    private val audioRepository: AudioRepository
) : ViewModel() {
    val historyState = historyRepo
        .getHistoriesIdsMapWithLastTime()
        .flatMapLatest { map ->
            val ids = map.toList()
                .sortedByDescending { it.second }
                .map { it.first }
            audioRepository.getAudios(ids)
        }.map { list -> list.map { it.toLegacyAudio() }.take(6) }
        .toState(emptyList(), viewModelScope)

    val pager = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = true,
        ),
        pagingSourceFactory = { historyRepo.getAllData() }
    ).flow.cachedIn(viewModelScope)

    fun getHistoryPlayedIds(block: (list: List<String>) -> Unit) = viewModelScope.launch {
        val list = historyRepo.getHistoriesIdsMapWithLastTime()
            .firstOrNull()
            ?.toList()
            ?.sortedByDescending { it.second }
            ?.map { it.first }
            ?: emptyList()
        block(list)
    }
}
