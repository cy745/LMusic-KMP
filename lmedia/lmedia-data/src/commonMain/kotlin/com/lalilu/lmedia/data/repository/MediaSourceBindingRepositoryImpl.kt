package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.LMediaKV
import com.lalilu.common.ext.io
import com.lalilu.lmedia.data.database.AudioLibraryCounts
import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.domain.repository.MediaLibrarySummary
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.source.MediaSourcePatchSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.io + SupervisorJob()),
) : MediaSourceBindingRepository {
    private val startMutex = Mutex()
    private var started = false
    private val committers = mutableMapOf<String, SourceSnapshotCommitter>()
    private val sourceMutexes = platformSource.sources.associate { it.name to Mutex() }
    private val mutableStates = MutableStateFlow(
        platformSource.sources.associate { source ->
            source.name to SourceStatus(enabled = platformSource.isEnabled(source))
        }
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

    override suspend fun <T> withEnabledSource(sourceName: String, block: suspend () -> T): T {
        startBinding()
        val mutex = sourceMutexes[sourceName] ?: error("Unknown media source: $sourceName")
        return mutex.runWhileSourceEnabled({ platformSource.isEnabled(sourceName) }, block)
    }

    override suspend fun startBinding() {
        startMutex.withLock {
            if (started) return
            started = true

            platformSource.sources.forEach { source ->
                val enabled = platformSource.isEnabled(source)
                val committer = SourceSnapshotCommitter(
                    commit = { snapshot ->
                        database.mediaDao().insert(snapshot = snapshot, sourceName = source.name)
                        if (kv.clearUnavailableAfterSync.value) {
                            database.mediaDao().clearUnavailableMedia(
                                activeSourceNames = platformSource.enabledSources.map { it.name },
                            )
                        }
                    },
                    onStateChanged = { commitState ->
                        updateStatus(source.name) { it.copy(commitState = commitState) }
                    },
                    acceptingSnapshots = enabled,
                )
                committers[source.name] = committer

                source.state
                    .onEach { syncState ->
                        updateStatus(source.name) { it.copy(syncState = syncState) }
                    }
                    .launchIn(scope)

                source.contentState
                    .onEach { content ->
                        updateStatus(source.name) { it.copy(contentAvailability = content.availability) }
                    }
                    .launchIn(scope)

                source.snapshot
                    .filterNotNull()
                    .distinctUntilChangedBy { it.revision }
                    .onEach { snapshot ->
                        if (!platformSource.isEnabled(source)) return@onEach
                        updateStatus(source.name) {
                            it.copy(
                                resultRevision = snapshot.revision,
                                songCount = snapshot.audios.size,
                            )
                        }
                        committer.submit(snapshot)
                    }
                    .launchIn(scope)

                // 单曲增量通道：与快照提交共用源级锁，逐条入库而不用重建整个媒体库。
                // 失败不写 commitState（那是完整快照的语义），只让"这条路坏了"可见。
                if (source is MediaSourcePatchSource) {
                    source.audioPatches
                        .onEach { audio ->
                            if (!platformSource.isEnabled(source)) return@onEach
                            committer.submitPatch {
                                database.mediaDao().upsertAudio(audio)
                            }.onSuccess {
                                updateStatus(source.name) { status ->
                                    if (status.patchFailures == 0 && status.lastPatchError == null) {
                                        status
                                    } else {
                                        status.copy(patchFailures = 0, lastPatchError = null)
                                    }
                                }
                            }.onFailure { throwable ->
                                updateStatus(source.name) { status ->
                                    status.copy(
                                        patchFailures = status.patchFailures + 1,
                                        lastPatchError = throwable.message ?: "单曲入库失败",
                                    )
                                }
                            }
                        }
                        .launchIn(scope)
                }

                try {
                    if (enabled) {
                        source.init()
                    } else {
                        source.deactivate()
                        database.mediaDao().markAudiosFromSourceUnavailable(source.name)
                        if (kv.clearUnavailableAfterSync.value) {
                            database.mediaDao().clearUnavailableMedia(
                                activeSourceNames = platformSource.enabledSources.map { it.name },
                            )
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    updateStatus(source.name) {
                        it.copy(enablementError = throwable.message ?: "数据源初始化失败")
                    }
                }
            }
        }
    }

    override suspend fun retryCommit(sourceName: String): Boolean {
        startBinding()
        if (!platformSource.isEnabled(sourceName)) return false
        return committers[sourceName]?.retry() ?: false
    }

    override suspend fun setSourceEnabled(sourceName: String, enabled: Boolean): Boolean {
        startBinding()
        val source = platformSource.sources.firstOrNull { it.name == sourceName } ?: return false
        val committer = committers[sourceName] ?: return false
        val sourceMutex = sourceMutexes[sourceName] ?: return false

        return withContext(NonCancellable) {
            sourceMutex.withLock {
                val previousError = states.value[sourceName]?.enablementError
                updateStatus(sourceName) {
                    it.copy(enablementChanging = true, enablementError = null)
                }

                try {
                    if (!needsEnablementReconciliation(
                            currentEnabled = platformSource.isEnabled(source),
                            targetEnabled = enabled,
                            previousError = previousError,
                        )
                    ) {
                        updateStatus(sourceName) { it.copy(enablementChanging = false) }
                        return@withLock true
                    }

                    if (enabled) {
                        committer.activate()
                        if (!platformSource.isEnabled(source)) {
                            platformSource.setEnabled(sourceName, true)
                        }
                        updateStatus(sourceName) {
                            it.copy(enabled = true)
                        }
                        source.init()
                        updateStatus(sourceName) { it.copy(enablementChanging = false) }
                    } else {
                        if (platformSource.isEnabled(source)) {
                            platformSource.setEnabled(sourceName, false)
                        }
                        updateStatus(sourceName) { it.copy(enabled = false) }
                        committer.deactivate {
                            var deactivationFailure: Throwable? = null
                            try {
                                source.deactivate()
                            } catch (throwable: Throwable) {
                                deactivationFailure = throwable
                            }

                            database.mediaDao().markAudiosFromSourceUnavailable(sourceName)
                            if (kv.clearUnavailableAfterSync.value) {
                                database.mediaDao().clearUnavailableMedia(
                                    activeSourceNames = platformSource.enabledSources.map { it.name },
                                )
                            }
                            deactivationFailure?.let { throw it }
                        }
                        updateStatus(sourceName) { it.copy(enablementChanging = false) }
                    }
                    true
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    updateStatus(sourceName) { it.copy(enablementChanging = false) }
                    throw cancelled
                } catch (throwable: Throwable) {
                    updateStatus(sourceName) {
                        it.copy(
                            enabled = platformSource.isEnabled(source),
                            enablementChanging = false,
                            enablementError = throwable.message ?: "数据源状态切换失败",
                        )
                    }
                    false
                }
            }
        }
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

/**
 * 用户意图已经持久化、但初始化或清理失败时，开关值会与目标值相同；此时仍必须重跑副作用。
 */
internal fun needsEnablementReconciliation(
    currentEnabled: Boolean,
    targetEnabled: Boolean,
    previousError: String?,
): Boolean = currentEnabled != targetEnabled || previousError != null
