/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
