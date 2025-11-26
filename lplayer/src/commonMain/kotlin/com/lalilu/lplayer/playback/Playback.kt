package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow


interface Playback {
    // Controls
    suspend fun play()
    suspend fun pause()
    suspend fun togglePlayPause()
    suspend fun stop()
    suspend fun skipTo(index: Int)

    // TODO suspend fun skipTo(index: Int, start: Boolean)
    suspend fun skipToNext()
    suspend fun skipToPrevious()
    suspend fun seekTo(positionMs: Long)

    // Queue Management
    val playlist: StateFlow<List<LItem>>
    val currentItem: StateFlow<LAudio?>
    val currentItemIndex: StateFlow<Int>

    suspend fun updatePlaylist(playlist: List<LItem>)
    suspend fun updatePlaylist(playlist: List<LItem>, startIndex: Int, start: Boolean)
    suspend fun clearPlaylist()

    // Playback State
    val isPlaying: StateFlow<Boolean>
    val playbackState: StateFlow<PlaybackState>
    val errors: SharedFlow<Throwable>
    val playbackMode: StateFlow<PlaybackMode>

    // Playback Information
    val currentDuration: StateFlow<Long>
    fun currentPosition(): Long
    val currentBufferedPosition: StateFlow<Long>

    // Utility Properties
    val canSeek: StateFlow<Boolean>
    val canSkipNext: StateFlow<Boolean>
    val canSkipPrevious: StateFlow<Boolean>

    // Playback Mode
    suspend fun setPlaybackMode(mode: PlaybackMode)
}