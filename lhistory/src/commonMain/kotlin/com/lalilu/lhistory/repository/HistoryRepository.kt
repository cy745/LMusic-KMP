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
import com.lalilu.lhistory.entity.LHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

interface HistoryRepository {
    suspend fun getUnUsedPreSaveHistory(mediaId: String): LHistory?
    suspend fun preSaveHistory(history: LHistory): Long
    suspend fun updateHistory(id: Long, duration: Long, repeatCount: Int, startTime: Long)
    fun clearHistories()

    fun getAllData(): PagingSource<Int, LHistory>
    fun getHistoriesFlow(limit: Int): Flow<List<LHistory>>
    fun getHistoriesWithCount(limit: Int): Flow<Map<LHistory, Int>>
    fun getHistoriesIdsMapWithCount(): Flow<Map<String, Int>>
    fun getHistoriesIdsMapWithLastTime(): Flow<Map<String, Long>>
    fun getHistoriesCountByMediaId(mediaId: String): Int
    fun getHistoriesLastTimeByMediaId(mediaId: String): Long
}

@Single
class HistoryRepositoryImpl(
    private val database: ILHistoryDatabase
) : HistoryRepository, CoroutineScope {
    private val historyDao: LHistoryDao by lazy { database.historyDao() }
    override val coroutineContext: CoroutineContext = Dispatchers.io

    private val countMap = historyDao
        .getFlowIdsMapWithCount(Int.MAX_VALUE)
        .distinctUntilChanged()
        .toCachedFlow()
        .also { it.launchIn(this) }

    private val lastTimeMap = historyDao
        .getFlowIdsMapWithLastTime(Int.MAX_VALUE)
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

    override fun clearHistories() {
        launch { historyDao.clear() }
    }

    override fun getAllData(): PagingSource<Int, LHistory> {
        return historyDao.getAllData()
    }

    override fun getHistoriesFlow(limit: Int): Flow<List<LHistory>> {
        return historyDao
            .getFlow(limit)
            .distinctUntilChanged()
    }

    override fun getHistoriesWithCount(limit: Int): Flow<Map<LHistory, Int>> {
        return historyDao
            .getFlowWithCount(limit)
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
