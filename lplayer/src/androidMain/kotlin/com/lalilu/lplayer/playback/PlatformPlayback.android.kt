package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

actual fun platformPlayback(): Playback {
    return object : Playback {
        override suspend fun play() {
        }

        override suspend fun pause() {
        }

        override suspend fun togglePlayPause() {
        }

        override suspend fun stop() {
        }

        override suspend fun skipTo(index: Int) {
        }

        override suspend fun skipToNext() {
        }

        override suspend fun skipToPrevious() {
        }

        override suspend fun seekTo(positionMs: Long) {
        }

        override val playlist: StateFlow<List<LItem>>
            get() = MutableStateFlow(emptyList())

        override val currentItem: StateFlow<LAudio?>
            get() = MutableStateFlow(null)

        override val currentItemIndex: StateFlow<Int>
            get() = MutableStateFlow(0)

        override suspend fun updatePlaylist(playlist: List<LItem>) {
        }

        override suspend fun clearPlaylist() {
        }

        override val isPlaying: StateFlow<Boolean>
            get() = MutableStateFlow(false)

        override val playbackState: StateFlow<PlaybackState>
            get() = MutableStateFlow(PlaybackState.Idle)

        override val errors: SharedFlow<Throwable>
            get() = MutableSharedFlow()

        override val playbackMode: StateFlow<PlaybackMode>
            get() = MutableStateFlow(PlaybackMode.SEQUENTIAL)

        override val currentDuration: StateFlow<Long>
            get() = MutableStateFlow(0L)

        override fun currentPosition(): Long {
            return 0L
        }

        override val currentBufferedPosition: StateFlow<Long>
            get() = MutableStateFlow(0L)

        override val canSeek: StateFlow<Boolean>
            get() = MutableStateFlow(false)

        override val canSkipNext: StateFlow<Boolean>
            get() = MutableStateFlow(false)

        override val canSkipPrevious: StateFlow<Boolean>
            get() = MutableStateFlow(false)

        override suspend fun setPlaybackMode(mode: PlaybackMode) {
        }
    }
}