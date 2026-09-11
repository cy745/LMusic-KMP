package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import kotlinx.coroutines.flow.*


interface Playback {
    // Playback State
    val isPlaying: StateFlow<Boolean>
    val errors: SharedFlow<Throwable>
    val playbackMode: StateFlow<PlaybackMode>

    // Playback Information
    val currentDuration: StateFlow<Long>
    fun currentPosition(): Long
    val currentBufferedPosition: StateFlow<Long>

    // Queue Management
    val queue: PlayableQueue

    // Utility Properties
    val canSeek: Flow<Boolean>
        get() = currentDuration.map { it > 0 }
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
