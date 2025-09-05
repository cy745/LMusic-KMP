package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow


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
    fun currentPlaybackState(): StateFlow<PlaybackState>

    fun currentDuration(): Long
    fun currentPosition(): Long
    fun currentBufferedPosition(): Long
    fun errorMessage(): SharedFlow<Throwable>
}