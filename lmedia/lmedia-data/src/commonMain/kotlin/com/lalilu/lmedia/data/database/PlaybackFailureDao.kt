package com.lalilu.lmedia.data.database

import androidx.room3.Dao
import androidx.room3.Query
import com.lalilu.lmedia.data.entity.PlaybackFailureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackFailureDao {
    @Query("SELECT * FROM playback_failure")
    fun observeAll(): Flow<List<PlaybackFailureEntity>>

    @Query("INSERT OR REPLACE INTO playback_failure (playbackId, reason, occurredAtMillis) VALUES (:playbackId, :reason, :occurredAtMillis)")
    suspend fun record(playbackId: String, reason: String, occurredAtMillis: Long)

    @Query("DELETE FROM playback_failure WHERE playbackId = :playbackId")
    suspend fun clear(playbackId: String)
}
