/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.lhistory.repository

import androidx.paging.PagingSource
import com.lalilu.common.ext.io
import com.lalilu.common.flow.toCachedFlow
import com.lalilu.lhistory.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

interface HistoryRepository {
    val revisions: StateFlow<Long>
    suspend fun inspect(): String
    suspend fun saveSession(history: LHistory, days: List<HistoryDay>): Long
    fun dailyListening(from: Long, until: Long): Flow<List<DailyListening>>
    fun statistics(from: Long, until: Long): Flow<ListeningStats>

    suspend fun getUnUsedPreSaveHistory(mediaId: String): LHistory?
    suspend fun preSaveHistory(history: LHistory): Long
    suspend fun updateHistory(id: Long, duration: Long, repeatCount: Int, startTime: Long)
    suspend fun clearHistories()

    fun getRange(from: Long, until: Long): PagingSource<Int, LHistory>
    fun getAllData(): PagingSource<Int, LHistory>
    fun getHistoriesFlow(limit: Int): Flow<List<LHistory>>
    fun getHistoriesWithCount(limit: Int): Flow<Map<LHistory, Int>>
    fun getHistoriesIdsMapWithCount(): Flow<Map<String, Int>>
    fun getHistoriesIdsMapWithLastTime(): Flow<Map<String, Long>>
    fun getHistoriesCountByMediaId(mediaId: String): Int
    fun getHistoriesLastTimeByMediaId(mediaId: String): Long
}

@OptIn(ExperimentalCoroutinesApi::class)
@Single
class HistoryRepositoryImpl(
    private val database: ILHistoryDatabase
) : HistoryRepository, CoroutineScope {
    private val historyDao: LHistoryDao by lazy { database.historyDao() }
    override val coroutineContext: CoroutineContext = Dispatchers.io + kotlinx.coroutines.SupervisorJob()

    private val revision = MutableStateFlow(0L)
    override val revisions = revision.asStateFlow()
    private val pages = MutableStateFlow<Set<PagingSource<Int, LHistory>>>(emptySet())
    private fun changed() {
        revision.update { it + 1 }
        pages.value.forEach { it.invalidate() }
    }
    private fun track(source: PagingSource<Int, LHistory>): PagingSource<Int, LHistory> {
        pages.update { it + source }
        source.registerInvalidatedCallback { pages.update { it - source } }
        return source
    }
    override suspend fun inspect() = "rows=${historyDao.recordCount()}, days=${historyDao.dayCount()}, revision=${revision.value}"

    private val countMap = revision.flatMapLatest { historyDao.getFlowIdsMapWithCount(Int.MAX_VALUE) }
        .distinctUntilChanged()
        .toCachedFlow()
        .also { it.launchIn(this) }

    private val lastTimeMap = revision.flatMapLatest { historyDao.getFlowIdsMapWithLastTime(Int.MAX_VALUE) }
        .distinctUntilChanged()
        .toCachedFlow()
        .also { it.launchIn(this) }

    override suspend fun getUnUsedPreSaveHistory(mediaId: String): LHistory? =
        withContext(Dispatchers.io) {
            historyDao.getLatestHistory()
                ?.takeIf { it.contentId == mediaId && it.duration <= 1000L }
        }

    override suspend fun preSaveHistory(history: LHistory): Long = withContext(Dispatchers.io) {
        historyDao.save(history.copy(duration = -1L))
    }

    override suspend fun updateHistory(
        id: Long,
        duration: Long,
        repeatCount: Int,
        startTime: Long
    ) {
        historyDao.updateHistory(
            id = id,
            duration = duration,
            repeatCount = repeatCount,
            startTime = startTime
        )
    }

    override suspend fun saveSession(history: LHistory, days: List<HistoryDay>): Long {
        val id = historyDao.saveSession(history, days)
        changed()
        return id
    }
    override fun dailyListening(from: Long, until: Long) = revision.flatMapLatest { historyDao.dailyListening(from, until) }
    override fun statistics(from: Long, until: Long) = revision.flatMapLatest { historyDao.statistics(from, until) }

    override suspend fun clearHistories() { historyDao.clear(); changed() }

    override fun getRange(from: Long, until: Long) = track(historyDao.getRange(from, until))

    override fun getAllData(): PagingSource<Int, LHistory> {
        return track(historyDao.getAllData())
    }

    override fun getHistoriesFlow(limit: Int): Flow<List<LHistory>> {
        return revision.flatMapLatest { historyDao.getFlow(limit) }
            .distinctUntilChanged()
    }

    override fun getHistoriesWithCount(limit: Int): Flow<Map<LHistory, Int>> {
        return revision.flatMapLatest { historyDao.getFlowWithCount(limit) }
            .distinctUntilChanged()
    }

    override fun getHistoriesCountByMediaId(mediaId: String): Int {
        return countMap.get()?.get(mediaId) ?: 0
    }

    override fun getHistoriesLastTimeByMediaId(mediaId: String): Long {
        return lastTimeMap.get()?.get(mediaId) ?: 0L
    }

    override fun getHistoriesIdsMapWithCount(): Flow<Map<String, Int>> {
        return countMap
    }

    override fun getHistoriesIdsMapWithLastTime(): Flow<Map<String, Long>> {
        return lastTimeMap
    }
}
