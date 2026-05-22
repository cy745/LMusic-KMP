package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
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
    fun CoroutineScope.startRecording(playback: Playback)

    /**
     * 队列恢复完成后的生命周期回调。
     * [PlaybackHistoryImpl] 中默认为空，平台可按需 override 以设置播放器 position。
     */
    suspend fun onQueueRestored(snapshot: HistorySnapshot)
}

/**
 * [PlaybackHistory] 的默认通用实现。
 */
class PlaybackHistoryImpl(
    override val historyStorage: HistoryStorage = HistoryStorageImpl()
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

    override fun CoroutineScope.startRecording(playback: Playback) {
        // 监听队列变化 → 持久化 playlist 信息
        playback.queue.expandedItems
            .onEach { state ->
                historyStorage.savePlayId(state.currentItem()?.idValue() ?: "")
                historyStorage.savePlaylistIds(state.list.map { it.idValue() })
            }
            .launchIn(this)

        // 监听播放状态 → 持久化 position
        // 使用 transformLatest 确保前一个 position 循环在状态切换时自动取消
        playback.isPlaying
            .transformLatest<Boolean, Unit> { isPlaying ->
                if (isPlaying) {
                    while (isActive) {
                        historyStorage.savePosition(playback.currentPosition())
                        delay(1000.milliseconds)
                    }
                }
            }
            .launchIn(this)
    }
}
