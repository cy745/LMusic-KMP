package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MediaSourceBindingRepository {
    val states: StateFlow<Map<String, SourceStatus>>
    val summary: StateFlow<MediaLibrarySummary>

    fun getSources(): PlatformMediaSource
    fun observeSource(name: String): Flow<SourceStatus?>
    suspend fun startBinding()

    /** 重新提交该数据源最近一次完整成功结果；没有可重试结果时返回 false。 */
    suspend fun retryCommit(sourceName: String): Boolean
}

sealed interface SnapshotCommitState {
    data object Idle : SnapshotCommitState
    data class Committing(val revision: Long) : SnapshotCommitState
    data class Committed(val revision: Long) : SnapshotCommitState
    data class Failed(val revision: Long, val message: String) : SnapshotCommitState
}

data class SourceStatus(
    val syncState: SnapshotState = SnapshotState.Idle,
    val resultRevision: Long? = null,
    val songCount: Int = 0,
    val commitState: SnapshotCommitState = SnapshotCommitState.Idle,
)

data class MediaLibrarySummary(
    val refreshingSources: Set<String> = emptySet(),
    val syncFailures: Map<String, String> = emptyMap(),
    val committingSources: Set<String> = emptySet(),
    val commitFailures: Map<String, String> = emptyMap(),
    val databaseSongCount: Int = 0,
    val availableSongCount: Int = 0,
    val unavailableSongCount: Int = 0,
)
