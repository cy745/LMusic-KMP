package com.lalilu.lhistory.repository

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.MapColumn
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.lalilu.lhistory.entity.LHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface LHistoryDao {
    @Insert
    suspend fun insert(history: LHistory): Long

    @Update
    suspend fun update(history: LHistory)

    @Delete
    suspend fun delete(history: LHistory)

    @Query("DELETE FROM m_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM m_history")
    suspend fun deleteAll()

    @Transaction
    @Query("SELECT * FROM m_history ORDER BY start_time DESC")
    fun getAllHistory(): Flow<List<LHistory>>

    @Transaction
    @Query("SELECT * FROM m_history WHERE id = :id")
    fun getHistory(id: Long): Flow<LHistory?>

    @Transaction
    @Query("SELECT * FROM m_history WHERE content_id = :audioId ORDER BY start_time DESC LIMIT 1")
    fun getHistoryByAudioId(audioId: String): Flow<LHistory?>

    @Query("SELECT * FROM m_history ORDER BY start_time DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<LHistory>>

    @Query("SELECT content_id, MAX(start_time) as lastTime FROM m_history GROUP BY content_id ORDER BY lastTime DESC LIMIT :count")
    fun getRecentHistoryIdsWithLastTime(count: Int): Flow<Map<@MapColumn("content_id") String, @MapColumn("lastTime") Long>>

    @Query("SELECT COUNT(*) FROM m_history")
    fun getHistoryCount(): Flow<Int>

    /**
     * 更新历史记录的播放时长、重复次数和开始时间
     */
    @Query("UPDATE m_history SET duration = :duration, repeat_count = :repeatCount, start_time = :startTime WHERE id = :id")
    suspend fun updateHistory(id: Long, duration: Long, repeatCount: Int, startTime: Long)

    /**
     * 获取指定内容ID的未使用预保存历史记录（duration = -1）
     */
    @Query("SELECT * FROM m_history WHERE content_id = :contentId AND duration = -1 LIMIT 1")
    suspend fun getUnUsedPreSaveHistory(contentId: String): LHistory?
}