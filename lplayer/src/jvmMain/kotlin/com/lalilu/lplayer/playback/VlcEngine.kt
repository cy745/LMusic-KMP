package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.caprica.vlcj.player.base.*

/** Native decoder only; no queue, retries, history or mode policy. */
internal class VlcEngine(private val scope: CoroutineScope, private val provider: suspend () -> MediaPlayer) : PlaybackEngine {
    override val state = MutableStateFlow(PlaybackEngineState.EMPTY)
    override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null
    var player: MediaPlayer? = null
        private set
    private var watch: Job? = null
    private var listener: MediaPlayerEventAdapter? = null
    private var generation = 0L
    private var gain = 0f
    override fun canHandle(mediaData: MediaData, audio: LAudio) = true
    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        release()
        val token = ++generation
        state.value = PlaybackEngineState(isLoading = true)
        val native = player ?: provider().also { player = it }
        native.audio().setVolume((gain * 100).toInt())
        listener = object : MediaPlayerEventAdapter() {
            override fun finished(mediaPlayer: MediaPlayer?) { scope.launch {
                if (token == generation && !state.value.isLoading) {
                    state.value = state.value.copy(isPlaying = false)
                    onEvent?.invoke(PlaybackEngineEvent.Completion)
                }
            } }
        }.also { native.events().addMediaPlayerEventListener(it) }
        startVlcMedia(native, mediaData)
        watch = scope.launch {
            var prepared = false
            while (isActive && token == generation) {
                val status = native.status().state()
                if (status == State.ERROR || (status == State.ENDED && !prepared)) {
                    state.value = state.value.copy(isLoading = false, error = "VLC failed to prepare audio")
                    return@launch
                }
                if (!prepared && status == State.PAUSED && native.audio().track() >= 0) prepared = true
                state.value = PlaybackEngineState(isLoading = !prepared, isPlaying = native.status().isPlaying,
                    position = currentPosition(), duration = native.status().length().coerceAtLeast(0))
                delay(50)
            }
        }
    }
    override suspend fun play() { player?.controls()?.play() }
    override suspend fun pause() { player?.controls()?.setPause(true) }
    override suspend fun stop() { player?.controls()?.stop() }
    override suspend fun seekTo(positionMs: Long) {
        val native = player ?: return
        native.controls().setTime(positionMs)
        withTimeout(5_000) { while (kotlin.math.abs(currentPosition() - positionMs) > 500) delay(20) }
    }
    fun setVolume(value: Float) { gain = value; player?.audio()?.setVolume((value * 100).toInt()) }
    override fun currentPosition() = player?.status()?.time()?.coerceAtLeast(0) ?: 0L
    override suspend fun release() {
        generation++
        watch?.cancel()
        listener?.let { player?.events()?.removeMediaPlayerEventListener(it) }
        listener = null
        player?.controls()?.stop()
        state.value = PlaybackEngineState.EMPTY
    }
}
