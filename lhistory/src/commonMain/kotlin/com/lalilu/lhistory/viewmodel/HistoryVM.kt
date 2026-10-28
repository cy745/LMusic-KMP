@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.lalilu.lhistory.viewmodel

import com.lalilu.extensions.toQueryState

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.lalilu.extensions.toState
import com.lalilu.lhistory.repository.HistoryRepository
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.getPlaybackSlots
import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import kotlin.time.Clock
import com.lalilu.lhistory.PlaybackDataTracker
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class HistoryVM(
    val historyRepo: HistoryRepository,
    private val audioRepository: AudioRepository,
    private val tracker: PlaybackDataTracker
) : ViewModel() {
    val historyState = historyRepo
        .getHistoriesIdsMapWithLastTime()
        .flatMapLatest { map ->
            val ids = map.toList()
                .sortedByDescending { it.second }
                .map { it.first }.take(6)
            audioRepository.getPlaybackSlots(ids).map { it.filterNotNull() }
        }
        .toQueryState(emptyList(), viewModelScope)

    val period = MutableStateFlow(StatsPeriod.Week)
    val filter = MutableStateFlow(HistoryFilter.All)
    val calendar = flow {
        while (true) {
            val zone = TimeZone.currentSystemDefault()
            emit(HistoryCalendar(Clock.System.now().toLocalDateTime(zone).date, zone))
            delay(30000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
        HistoryCalendar(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date, TimeZone.currentSystemDefault()))

    val statistics = combine(calendar, period) { date, period -> date to period }.flatMapLatest { (date, period) ->
        val today = date.range(HistoryFilter.Today)
        val range = date.range(if (period == StatsPeriod.Week) HistoryFilter.Week else HistoryFilter.Month)
        combine(historyRepo.statistics(today.from, today.until), historyRepo.statistics(range.from, range.until),
            historyRepo.dailyListening(range.from, range.until)) { day, totals, rows ->
            HistoryStatistics(day, totals, date.bars(period, rows))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryStatistics())

    val pager = combine(calendar, filter) { date, filter -> date.range(filter) }
        .distinctUntilChanged().flatMapLatest { range ->
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                historyRepo.getRange(range.from, range.until)
            }.flow
        }.cachedIn(viewModelScope)

    suspend fun clearHistory() = tracker.clearHistory()

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
