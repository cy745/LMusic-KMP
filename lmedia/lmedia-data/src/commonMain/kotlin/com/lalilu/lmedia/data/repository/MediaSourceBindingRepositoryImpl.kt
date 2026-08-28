package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.LMediaKV
import com.lalilu.common.ext.io
import com.lalilu.lmedia.data.database.AudioLibraryCounts
import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.domain.repository.MediaLibrarySummary
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * Auto-starts MediaSource → database binding on creation.
 * 每个数据源独立收集状态与完整成功结果；数据库提交在同一数据源内保持顺序，源之间不互相等待。
 */
@Single(binds = [MediaSourceBindingRepository::class], createdAtStart = true)
class MediaSourceBindingRepositoryImpl(
    private val platformSource: PlatformMediaSource,
    private val database: ILMediaDatabase,
    private val kv: LMediaKV,
) : MediaSourceBindingRepository {
    private val scope = CoroutineScope(Dispatchers.io + SupervisorJob())
    private val startMutex = Mutex()
    private var started = false
    private val committers = mutableMapOf<String, SourceSnapshotCommitter>()
    private val mutableStates = MutableStateFlow(
        platformSource.sources.associate { it.name to SourceStatus() }
    )

    override val states: StateFlow<Map<String, SourceStatus>> = mutableStates.asStateFlow()
    override val summary: StateFlow<MediaLibrarySummary> = combine(
        states,
        database.audioDao().observeLibraryCounts(),
        ::buildSummary,
    )
        .stateIn(scope, SharingStarted.Eagerly, MediaLibrarySummary())

    init {
        scope.launch { startBinding() }
    }

    override fun getSources(): PlatformMediaSource = platformSource

    override fun observeSource(name: String): Flow<SourceStatus?> = states.map { it[name] }

    override suspend fun startBinding() {
        startMutex.withLock {
            if (started) return
            started = true

            platformSource.sources.forEach { source ->
                val committer = SourceSnapshotCommitter(
                    commit = { snapshot ->
                        database.mediaDao().insert(snapshot = snapshot, sourceName = source.name)
                        if (kv.clearUnavailableAfterSync.value) {
                            database.mediaDao().clearUnavailableMedia(
                                activeSourceNames = platformSource.sources.map { it.name },
                            )
                        }
                    },
                    onStateChanged = { commitState ->
                        updateStatus(source.name) { it.copy(commitState = commitState) }
                    },
                )
                committers[source.name] = committer

                source.state
                    .onEach { syncState ->
                        updateStatus(source.name) { it.copy(syncState = syncState) }
                    }
                    .launchIn(scope)

                source.snapshot
                    .filterNotNull()
                    .distinctUntilChangedBy { it.revision }
                    .onEach { snapshot ->
                        updateStatus(source.name) {
                            it.copy(
                                resultRevision = snapshot.revision,
                                songCount = snapshot.audios.size,
                            )
                        }
                        committer.submit(snapshot)
                    }
                    .launchIn(scope)
            }
        }
    }

    override suspend fun retryCommit(sourceName: String): Boolean {
        startBinding()
        return committers[sourceName]?.retry() ?: false
    }

    private fun updateStatus(name: String, transform: (SourceStatus) -> SourceStatus) {
        mutableStates.update { current ->
            current + (name to transform(current[name] ?: SourceStatus()))
        }
    }

    private fun buildSummary(
        statuses: Map<String, SourceStatus>,
        counts: AudioLibraryCounts,
    ): MediaLibrarySummary {
        val refreshing = statuses.filterValues { it.syncState is SnapshotState.Loading }.keys
        val syncFailures = statuses.mapNotNull { (name, status) ->
            (status.syncState as? SnapshotState.Error)?.message?.let { name to it }
        }.toMap()
        val commitFailures = statuses.mapNotNull { (name, status) ->
            (status.commitState as? SnapshotCommitState.Failed)?.message?.let { name to it }
        }.toMap()

        return MediaLibrarySummary(
            refreshingSources = refreshing,
            syncFailures = syncFailures,
            committingSources = statuses.filterValues {
                it.commitState is SnapshotCommitState.Committing
            }.keys,
            commitFailures = commitFailures,
            databaseSongCount = counts.total,
            availableSongCount = counts.available,
            unavailableSongCount = counts.unavailable,
        )
    }
}
