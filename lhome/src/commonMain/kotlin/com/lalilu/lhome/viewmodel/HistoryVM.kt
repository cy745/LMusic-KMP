package com.lalilu.lhome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lalilu.extensions.toState
import com.lalilu.lmedia.data.HistoryRepository
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import org.koin.core.annotation.Factory

@OptIn(ExperimentalCoroutinesApi::class)
@Factory
class HistoryVM(
    val historyRepo: HistoryRepository
) : ViewModel() {

    val historiesFlow: Flow<List<LHistory>> = historyRepo.getRecentHistoryFlow(50)

    val recentSongsFlow = historyRepo
        .getRecentHistoryIdsWithLastTime(count = 6)
        .flatMapLatest { map -> LMedia.instance.mapByFlow<LAudio>(map.keys.toList()) }
        .toState(emptyList(), viewModelScope)
}