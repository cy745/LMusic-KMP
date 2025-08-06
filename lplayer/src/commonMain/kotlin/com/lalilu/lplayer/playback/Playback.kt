package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.flow.StateFlow

sealed class PlaybackState {
    data object Idle : PlaybackState()
    data class Loading(val item: LItem) : PlaybackState()
    data class Playing(val item: LItem) : PlaybackState()
    data class Paused(val item: LItem) : PlaybackState()
    data class Error(val error: Throwable) : PlaybackState()
    data object Ended : PlaybackState()
}

interface Playback {
    // Controls
    fun play()
    fun pause()
    fun togglePlayPause()
    fun stop()
    fun skipTo(index: Int)
    fun skipToNext()
    fun skipTpPrevious()
    fun seekTo(positionMs: Long)

    // Queue
    fun flattenPlaylist(): StateFlow<List<LAudio>>
    fun playlist(): StateFlow<List<LItem>>
    fun updatePlaylist(playlist: List<LItem>)
    fun clearPlaylist()

    // Infos
    fun isPlaying(): StateFlow<Boolean>
    fun currentItem(): StateFlow<LAudio?>
    fun currentItemIndex(): StateFlow<Int>

    fun currentDuration(): Long
    fun currentPosition(): Long
    fun currentBufferedPosition(): Long
}