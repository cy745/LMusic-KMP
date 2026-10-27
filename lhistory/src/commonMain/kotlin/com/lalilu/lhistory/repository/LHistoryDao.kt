package com.lalilu.lhistory.repository

import androidx.paging.PagingSource
import androidx.room3.*
import com.lalilu.lhistory.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LHistoryDao {
    @Query("SELECT COUNT(*) FROM m_history")
    suspend fun recordCount(): Long
    @Query("SELECT COUNT(*) FROM history_day")
    suspend fun dayCount(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(history: LHistory): Long

    @Update(entity = LHistory::class)
    suspend fun update(vararg history: LHistory)

    @Query("UPDATE m_history SET duration = :duration, repeatCount = :repeatCount, startTime = :startTime WHERE id = :id;")
    suspend fun updateHistory(id: Long, duration: Long, repeatCount: Int, startTime: Long)

    @Query("DELETE FROM m_history;")
    suspend fun clearRecords()
    @Query("DELETE FROM history_day;")
    suspend fun clearDays()
    @Transaction
    suspend fun clear() { clearDays(); clearRecords() }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDays(days: List<HistoryDay>)

    @Transaction
    suspend fun saveSession(history: LHistory, days: List<HistoryDay>): Long {
        val id = save(history)
        saveDays(days.map { it.copy(historyId = id) })
        return id
    }

    @Query("SELECT COALESCE(SUM(d.duration), 0) AS duration, COALESCE(SUM(d.playCount), 0) AS playCount, COUNT(DISTINCT h.contentId) AS songCount FROM history_day d JOIN m_history h ON h.id = d.historyId WHERE d.dayStart >= :from AND d.dayStart < :until")
    fun statistics(from: Long, until: Long): Flow<ListeningStats>


    @Query("SELECT d.dayStart AS dayStart, SUM(d.duration) AS duration FROM history_day d JOIN m_history h ON h.id = d.historyId WHERE d.dayStart >= :from AND d.dayStart < :until GROUP BY d.dayStart ORDER BY d.dayStart")
    fun dailyListening(from: Long, until: Long): Flow<List<DailyListening>>

    @Delete(entity = LHistory::class)
    suspend fun delete(vararg history: LHistory)

    @Query("SELECT * FROM m_history WHERE duration >= 0 ORDER BY startTime DESC, id DESC")
    fun getAllData(): PagingSource<Int, LHistory>

    @Query("SELECT * FROM m_history WHERE duration >= 0 AND startTime >= :from AND startTime < :until ORDER BY startTime DESC, id DESC")
    fun getRange(from: Long, until: Long): PagingSource<Int, LHistory>

    @Query("SELECT * FROM m_history WHERE id = :id;")
    suspend fun getById(id: Long): LHistory?

    @Query("SELECT * FROM m_history ORDER BY id DESC LIMIT 1")
    suspend fun getLatestHistory(): LHistory?

    /**
     * 查询播放历史，去除重复的记录，只保留最近的一条，按照最近播放时间排序
     */
    @Query(
        "SELECT * FROM " +
                "(SELECT id, contentId, contentTitle, parentId, parentTitle, duration, repeatCount, playCount, max(startTime) as 'startTime' FROM m_history WHERE duration >= 0 GROUP BY contentId) as A " +
                "ORDER BY A.startTime DESC LIMIT :limit;"
    )
    fun getFlow(limit: Int): Flow<List<LHistory>>

    /**
     * 查询播放历史，按照最近播放时间排序且计算每首歌的播放次数
     */
    @Query(
        "SELECT * FROM " +
                "(SELECT id, contentId, contentTitle, parentId, parentTitle, duration, repeatCount, SUM(playCount) as playCount, SUM(playCount) as 'count', max(startTime) as 'startTime' FROM m_history WHERE duration >= 0 GROUP BY contentId) as A " +
                "ORDER BY A.startTime DESC LIMIT :limit;"
    )
    fun getFlowWithCount(limit: Int): Flow<Map<LHistory, @MapColumn(columnName = "count") Int>>

    @Query(
        "SELECT contentId, SUM(playCount) as 'count' FROM m_history WHERE duration >= 0 GROUP BY contentId " +
                "LIMIT :limit;"
    )
    fun getFlowIdsMapWithCount(limit: Int): Flow<Map<@MapColumn(columnName = "contentId") String, @MapColumn(columnName = "count") Int>>

    @Query(
        "SELECT contentId, max(startTime) as 'startTime' FROM m_history WHERE duration >= 0 GROUP BY contentId " +
                "LIMIT :limit;"
    )
    fun getFlowIdsMapWithLastTime(limit: Int): Flow<Map<@MapColumn(columnName = "contentId") String, @MapColumn(columnName = "startTime") Long>>
}