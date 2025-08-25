package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

actual fun platformPlayback(): Playback {
    return object : Playback {
        override fun play() {
        }

        override fun pause() {
        }

        override fun togglePlayPause() {
        }

        override fun stop() {
        }

        override fun skipTo(index: Int) {
        }

        override fun skipToNext() {
        }

        override fun skipTpPrevious() {
        }

        override fun seekTo(positionMs: Long) {
        }

        override fun flattenPlaylist(): StateFlow<List<LAudio>> {
            return MutableStateFlow(emptyList())
        }

        override fun playlist(): StateFlow<List<LItem>> {
            return MutableStateFlow(emptyList())
        }

        override fun updatePlaylist(playlist: List<LItem>) {
        }

        override fun clearPlaylist() {
        }

        override fun isPlaying(): StateFlow<Boolean> {
            return MutableStateFlow(false)
        }

        override fun currentItem(): StateFlow<LAudio?> {
            return MutableStateFlow(null)
        }

        override fun currentItemIndex(): StateFlow<Int> {
            return MutableStateFlow(0)
        }

        override fun currentPlaybackState(): StateFlow<PlaybackState> {
            return MutableStateFlow(PlaybackState.Idle)
        }

        override fun currentDuration(): Long {
            return 0L
        }

        override fun currentPosition(): Long {
            return 0L
        }

        override fun currentBufferedPosition(): Long {
            return 0L
        }

        override fun errorMessage(): SharedFlow<Throwable> {
            return MutableSharedFlow()
        }
    }
}