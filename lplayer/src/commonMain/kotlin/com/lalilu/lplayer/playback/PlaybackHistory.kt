package com.lalilu.lplayer.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.milliseconds

/**
 * 播放历史记录行为定义。
 * 由 [PlaybackHistoryImpl] 提供通用实现，Playback 实现类通过 `by` 委托获得能力。
 */
interface PlaybackHistory {
    val historyStorage: HistoryStorage

    /** 历史快照，包含恢复播放所需的全部信息。 */
    data class HistorySnapshot(
        val ids: List<String>,
        val index: Int,
        val position: Long
    )

    /**
     * 从 [historyStorage] 中读取历史快照。
     * 若无历史记录（playlist 为空）则返回 null。
     */
    fun restoreFromHistory(): HistorySnapshot?

    /**
     * 在当前 [CoroutineScope] 中启动播放状态录制。
     * 自动监听 `isPlaying` 和 `queue.expandedItems`，将状态持久化到 [historyStorage]。
     */
    fun CoroutineScope.startRecording(
        playback: Playback,
        restoreState: StateFlow<HistoryRestoreState>? = null,
    )

    /**
     * 队列恢复完成后的生命周期回调。
     * [PlaybackHistoryImpl] 中默认为空，平台可按需 override 以设置播放器 position。
     */
    suspend fun onQueueRestored(snapshot: HistorySnapshot)
}

/**
 * [PlaybackHistory] 的默认通用实现。
 */
@Single
class PlaybackHistoryImpl(
    override val historyStorage: HistoryStorage
) : PlaybackHistory {

    override fun restoreFromHistory(): PlaybackHistory.HistorySnapshot? {
        val ids = historyStorage.savedPlaylistIds()
        if (ids.isEmpty()) return null
        val id = historyStorage.savedPlayId()
        val index = ids.indexOf(id).coerceAtLeast(0)
        return PlaybackHistory.HistorySnapshot(ids, index, historyStorage.savedPosition())
    }

    override suspend fun onQueueRestored(snapshot: PlaybackHistory.HistorySnapshot) {
        // 默认空实现 — 平台可按需 override
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun CoroutineScope.startRecording(
        playback: Playback,
        restoreState: StateFlow<HistoryRestoreState>?,
    ) {
        // 监听队列变化 → 持久化 playlist 信息
        val queuePersistence = restoreState?.let { state ->
            combine(playback.queue.expandedItems, state) { queue, restore ->
                queue to (restore as? HistoryRestoreState.Pending)
            }
        } ?: playback.queue.expandedItems.map { queue -> queue to null }

        queuePersistence
            .onEach { (state, restore) ->
                val persistence = historyQueuePersistence(state, restore)
                persistence.currentId?.let(historyStorage::savePlayId)
                persistence.playlistIds?.let(historyStorage::savePlaylistIds)
            }
            .launchIn(this)

        // 缺失的历史 current 已回退，或用户在部分队列中主动改选时，旧 position 不再属于新歌曲。
        restoreState
            ?.map { state ->
                state is HistoryRestoreState.Pending &&
                    state.currentRestored &&
                    state.currentId == null
            }
            ?.distinctUntilChanged()
            ?.filter { it }
            ?.onEach { historyStorage.savePosition(0L) }
            ?.launchIn(this)

        // 监听播放状态 → 持久化 position
        // 使用 transformLatest 确保前一个 position 循环在状态切换时自动取消
        playback.isPlaying
            .transformLatest<Boolean, Unit> { isPlaying ->
                while (isActive) {
                    if (canPersistPlaybackPosition(restoreState?.value)) {
                        historyStorage.savePosition(playback.currentPosition())
                    }
                    if (!isPlaying) break
                    delay(1000.milliseconds)
                }
            }
            .launchIn(this)
    }
}

internal data class HistoryQueuePersistence(
    /** null 表示本次保留已持久化的历史 currentId。 */
    val currentId: String?,
    /** null 表示本次保留包含未解析 ID 的完整历史列表。 */
    val playlistIds: List<String>?,
)

/** 历史 current/position 尚未真正应用到播放器时，保留上一次持久化的位置。 */
internal fun canPersistPlaybackPosition(restoreState: HistoryRestoreState?): Boolean =
    (restoreState as? HistoryRestoreState.Pending)?.currentRestored != false

internal fun historyQueuePersistence(
    state: QueueState,
    restore: HistoryRestoreState.Pending?,
): HistoryQueuePersistence {
    val queueIds = state.list.map { it.id }
    val isRestoringPartialQueue = restore != null && queueIds == restore.resolvedIds
    val shouldPreserveCurrent = restore != null &&
        isRestoringPartialQueue &&
        restore.currentId != null &&
        restore.currentId !in restore.resolvedIds

    return HistoryQueuePersistence(
        currentId = if (shouldPreserveCurrent) null else state.currentItem()?.id.orEmpty(),
        playlistIds = if (isRestoringPartialQueue) null else queueIds,
    )
}
