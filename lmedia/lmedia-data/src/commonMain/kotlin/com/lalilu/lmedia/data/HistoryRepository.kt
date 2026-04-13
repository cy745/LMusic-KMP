package com.lalilu.lmedia.data

import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.data.database.LHistoryDao
import com.lalilu.lmedia.entity.LHistory
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

interface HistoryRepository {
    fun getAllHistory(): Flow<List<LHistory>>
    fun getHistory(id: Long): Flow<LHistory?>
    fun getHistoryByAudioId(audioId: String): Flow<LHistory?>
    fun getRecentHistory(limit: Int): Flow<List<LHistory>>
    fun getRecentHistoryFlow(limit: Int): Flow<List<LHistory>>
    fun getRecentHistoryIdsWithLastTime(count: Int): Flow<Map<String, Long>>
    fun getHistoryCount(): Flow<Int>
    suspend fun insert(history: LHistory): Long
    suspend fun update(history: LHistory)
    suspend fun delete(history: LHistory)
    suspend fun deleteById(id: Long)
    suspend fun deleteAll()

    /**
     * 更新历史记录的播放时长、重复次数和开始时间
     */
    suspend fun updateHistory(id: Long, duration: Long, repeatCount: Int, startTime: Long)

    /**
     * 获取指定内容ID的未使用预保存历史记录（duration = -1）
     */
    suspend fun getUnUsedPreSaveHistory(contentId: String): LHistory?

    /**
     * 预保存历史记录，用于追踪新的播放项
     */
    suspend fun preSaveHistory(history: LHistory): Long
}

@Single
class HistoryRepositoryImpl(
    private val database: ILMediaDatabase
) : HistoryRepository {
    private val historyDao: LHistoryDao by lazy { database.historyDao() }

    override fun getAllHistory(): Flow<List<LHistory>> =
        historyDao.getAllHistory()

    override fun getHistory(id: Long): Flow<LHistory?> =
        historyDao.getHistory(id)

    override fun getHistoryByAudioId(audioId: String): Flow<LHistory?> =
        historyDao.getHistoryByAudioId(audioId)

    override fun getRecentHistory(limit: Int): Flow<List<LHistory>> =
        historyDao.getRecentHistory(limit)

    override fun getRecentHistoryFlow(limit: Int): Flow<List<LHistory>> =
        historyDao.getRecentHistory(limit)

    override fun getRecentHistoryIdsWithLastTime(count: Int): Flow<Map<String, Long>> =
        historyDao.getRecentHistoryIdsWithLastTime(count)

    override fun getHistoryCount(): Flow<Int> =
        historyDao.getHistoryCount()

    override suspend fun insert(history: LHistory): Long =
        historyDao.insert(history)

    override suspend fun update(history: LHistory) =
        historyDao.update(history)

    override suspend fun delete(history: LHistory) =
        historyDao.delete(history)

    override suspend fun deleteById(id: Long) =
        historyDao.deleteById(id)

    override suspend fun deleteAll() =
        historyDao.deleteAll()

    override suspend fun updateHistory(id: Long, duration: Long, repeatCount: Int, startTime: Long) =
        historyDao.updateHistory(id, duration, repeatCount, startTime)

    override suspend fun getUnUsedPreSaveHistory(contentId: String): LHistory? =
        historyDao.getUnUsedPreSaveHistory(contentId)

    override suspend fun preSaveHistory(history: LHistory): Long =
        historyDao.insert(history)
}
