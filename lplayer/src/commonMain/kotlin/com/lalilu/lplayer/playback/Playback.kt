package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import kotlinx.coroutines.flow.*

/** 引擎不上报当前媒体标识时的占位：用到它的判断会退化成保守的"值变化"启发式。 */
private val UnknownEngineItemKey = MutableStateFlow<String?>(null)
private val EmptyPendingNext = MutableStateFlow<LAudio?>(null)


interface Playback {
    /** Presentation-only intent in random mode; it is not an extra physical queue entry. */
    val pendingNext: StateFlow<LAudio?> get() = EmptyPendingNext
    // Playback State
    val isPlaying: StateFlow<Boolean>
    val errors: SharedFlow<Throwable>
    val playbackMode: StateFlow<PlaybackMode>

    // Playback Information

    /** 引擎上报的原始时长。切歌瞬间会经过 0，展示用途请改用 [stableDuration]。 */
    val currentDuration: StateFlow<Long>

    /**
     * 引擎当前真正加载的媒体标识（[LAudio.playbackId]）；null 表示引擎不上报。
     *
     * [stableDuration] 用它判断"引擎报出的时长属于哪一首"：切换曲目时引擎可能在新曲目
     * 元数据到达之前就先报出了新时长（缓存命中时几乎必然如此），只比较"值有没有变过"
     * 会把这种情况误判成上一首的残留，时长就永远显示不出来。
     */
    val currentEngineItemKey: StateFlow<String?>
        get() = UnknownEngineItemKey

    /**
     * 当前曲目的稳定时长（毫秒），0 表示未知。
     *
     * 由"曲目元数据 + 引擎上报"按曲目合成：切歌时立刻可用，同一曲目内不会被引擎的
     * 瞬时 0 降级。迷你播放器、播放列表、系统通知等所有展示时长的位置都应读它。
     */
    val stableDuration: StateFlow<Long>
        get() = currentDuration

    fun currentPosition(): Long

    /**
     * 当前缓冲位置（毫秒）。
     *
     * 只对在线内容有意义：本地播放恒为 0，播放页因此不会渲染缓冲条
     * （引擎对本地文件也会上报加载缓冲，那不是网络进度）。
     *
     * 在线内容默认取引擎上报值；若当前曲目的数据源能给出自身的缓冲比例
     * （见 `MediaDataSource.bufferedProgress`，例如 WebDAV 的读穿缓存），
     * 则改用该比例换算，因为它才代表"已经可以不等下载直接播放到的范围"。
     */
    val currentBufferedPosition: StateFlow<Long>

    // Queue Management
    val queue: PlayableQueue

    // Utility Properties
    val canSeek: Flow<Boolean>
        get() = stableDuration.map { it > 0 }
    val canSkipNext: Flow<Boolean>
        get() = queue.expandedItems
            .combine(playbackMode) { currentState, playMode -> isAbleToSkipNext(currentState, playMode) }
    val canSkipPrevious: Flow<Boolean>
        get() = queue.expandedItems
            .combine(playbackMode) { currentState, playMode -> isAbleToSkipPrevious(currentState, playMode) }

    // Controls
    suspend fun play()
    suspend fun pause()
    suspend fun togglePlayPause()
    suspend fun stop()
    suspend fun skipTo(index: Int, start: Boolean)
    suspend fun skipToNext()
    suspend fun skipToPrevious()
    suspend fun seekTo(positionMs: Long)

    suspend fun updatePlaylist(playlist: List<LAudio>, startIndex: Int, start: Boolean)

    /** Select an existing source-qualified song, or insert it next to current, then play it. */
    suspend fun playAudio(audio: LAudio) {
        queue.update { selectOrInsert(audio) }
        skipTo(queue.stateSnapshot().index, start = true)
    }
    /** Queue edits that can affect the loaded item must go through the playback boundary. */
    suspend fun editQueue(block: QueueUpdateRequest.() -> Unit) {
        val previous = queue.currentItem()?.mediaKey
        val resume = isPlaying.value
        queue.update(block = block)
        val next = queue.stateSnapshot()
        if (next.currentItem()?.mediaKey != previous || next.list.isEmpty()) {
            stop()
            if (next.list.isNotEmpty()) skipTo(next.index, start = resume)
        }
    }

    suspend fun clearPlaylist() = editQueue { clear() }

    /** Atomically restore a failed edit only while its resulting queue still owns the player.
     * Implementations without a guarded restore leave the user's queue untouched. Never autoplay.
     */
    suspend fun restoreFailedQueueEdit(expected: QueueState, original: QueueState, position: Long): Boolean = false

    /** Receipt must be delivered inside the same edit boundary, after internal selection settles. */
    suspend fun editQueueWithReceipt(block: QueueUpdateRequest.() -> Unit, onApplied: (QueueState) -> Unit): Unit =
        error("This player does not support recoverable queue edits")

    /** Request the next actual playback, not merely a position in the displayed list. */
    suspend fun playNext(audio: LAudio) = editQueue { addToNext(listOf(audio)) }

    // Playback Mode
    suspend fun setPlaybackMode(mode: PlaybackMode)

    /**
     * 当播放完成时暂停播放
     *
     * @param cancel 是否取消
     */
    suspend fun setPauseWhenCompletion(cancel: Boolean = false)

    // Helper methods
    private fun isAbleToSkipNext(
        currentState: QueueState,
        playMode: PlaybackMode
    ): Boolean {
        val flattened = currentState.list

        return when (playMode) {
            PlaybackMode.SHUFFLE, PlaybackMode.LOOP -> flattened.size > 1
            PlaybackMode.SEQUENTIAL, PlaybackMode.SINGLE_LOOP -> currentState.index < flattened.size - 1
        }
    }

    private fun isAbleToSkipPrevious(
        currentState: QueueState,
        playMode: PlaybackMode
    ): Boolean {
        val flattened = currentState.list

        return when (playMode) {
            PlaybackMode.SHUFFLE, PlaybackMode.LOOP -> flattened.size > 1
            PlaybackMode.SEQUENTIAL, PlaybackMode.SINGLE_LOOP -> currentState.index > 0
        }
    }
}
