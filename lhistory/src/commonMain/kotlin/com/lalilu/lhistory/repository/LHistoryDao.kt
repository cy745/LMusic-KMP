package com.lalilu.lhistory.repository

import androidx.paging.PagingSource
import androidx.room3.*
import com.lalilu.lhistory.entity.LHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface LHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(history: LHistory): Long

    @Update(entity = LHistory::class)
    suspend fun update(vararg history: LHistory)

    @Query("UPDATE m_history SET duration = :duration, repeatCount = :repeatCount, startTime = :startTime WHERE id = :id;")
    suspend fun updateHistory(id: Long, duration: Long, repeatCount: Int, startTime: Long)

    @Query("DELETE FROM m_history;")
    suspend fun clear()

    @Delete(entity = LHistory::class)
    suspend fun delete(vararg history: LHistory)

    @Query("SELECT * FROM m_history ORDER BY startTime DESC")
    fun getAllData(): PagingSource<Int, LHistory>

    @Query("SELECT * FROM m_history WHERE id = :id;")
    suspend fun getById(id: Long): LHistory?

    @Query("SELECT * FROM m_history ORDER BY id DESC LIMIT 1")
    suspend fun getLatestHistory(): LHistory?

    /**
     * 查询播放历史，去除重复的记录，只保留最近的一条，按照最近播放时间排序
     */
    @Query(
        "SELECT * FROM " +
                "(SELECT id, contentId, contentTitle, parentId, parentTitle, duration, repeatCount, max(startTime) as 'startTime' FROM m_history GROUP BY contentId) as A " +
                "ORDER BY A.startTime DESC LIMIT :limit;"
    )
    fun getFlow(limit: Int): Flow<List<LHistory>>

    /**
     * 查询播放历史，按照最近播放时间排序且计算每首歌的播放次数
     */
    @Query(
        "SELECT * FROM " +
                "(SELECT id, contentId, contentTitle, parentId, parentTitle, duration, repeatCount, (count(contentId) + repeatCount) as 'count', max(startTime) as 'startTime' FROM m_history GROUP BY contentId) as A " +
                "ORDER BY A.startTime DESC LIMIT :limit;"
    )
    fun getFlowWithCount(limit: Int): Flow<Map<LHistory, @MapColumn(columnName = "count") Int>>

    @Query(
        "SELECT contentId, (count(contentId) + repeatCount) as 'count' FROM m_history GROUP BY contentId " +
                "LIMIT :limit;"
    )
    fun getFlowIdsMapWithCount(limit: Int): Flow<Map<@MapColumn(columnName = "contentId") String, @MapColumn(columnName = "count") Int>>

    @Query(
        "SELECT contentId, max(startTime) as 'startTime' FROM m_history GROUP BY contentId " +
                "LIMIT :limit;"
    )
    fun getFlowIdsMapWithLastTime(limit: Int): Flow<Map<@MapColumn(columnName = "contentId") String, @MapColumn(columnName = "startTime") Long>>
}