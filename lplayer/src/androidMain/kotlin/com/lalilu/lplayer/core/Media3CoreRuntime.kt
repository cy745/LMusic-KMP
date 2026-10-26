package com.lalilu.lplayer.core

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lalilu.playback.core.*
import kotlinx.coroutines.*

/** Native audio execution only. The service-owned core is the sole queue/policy owner. */
class Media3CoreRuntime(context: Context, dataSourceFactory: androidx.media3.datasource.DataSource.Factory? = null,
    private val itemFactory: (LoadTicket, PlayableResource) -> MediaItem = { ticket, resource ->
        MediaItem.Builder().setMediaId("${ticket.key.source.length}:${ticket.key.source}${ticket.key.id}").setUri(resource.uri).build()
    }, private val gain: Float = 1f,
) : AudioRuntime {
    override var onEvent: ((RuntimeEvent) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val player = ExoPlayer.Builder(context.applicationContext,
        androidx.media3.exoplayer.DefaultRenderersFactory(context).setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)).apply {
        dataSourceFactory?.let { setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(it)) }
    }.build()
    private var owner: LoadTicket? = null
    private var loaded: LoadTicket? = null
    private var listener: Player.Listener? = null
    private var transition: Job? = null
    private var fade: Job? = null
    private var pendingLoad: Job? = null
    private var desiredPlaying = false
    private val pendingSeeks = ArrayDeque<Pair<Long, Long>>()
    private var prepared = false
    private var preparationTimeout: Job? = null

    init {
        player.volume = gain
        scope.launch {
            while (isActive) {
                delay(100)
                val ticket = loaded ?: continue
                if (ticket == owner && prepared) {
                    onEvent?.invoke(RuntimeEvent.Position(ticket, player.currentPosition, duration(), player.bufferedPosition))
                }
            }
        }
    }

    override fun silence(next: LoadTicket?) {
        owner = next
        desiredPlaying = false
        pendingLoad?.cancel()
        preparationTimeout?.cancel()
        fade?.cancel()
        transition?.cancel()
        listener?.let(player::removeListener)
        listener = null
        loaded = null
        prepared = false
        pendingSeeks.clear()
        // Teardown may take a few frames, but accepting another selection never waits for it.
        transition = scope.launch {
            if (player.isPlaying) ramp(0f)
            player.stop()
            player.clearMediaItems()
        }
    }

    override fun prepare(ticket: LoadTicket, resource: PlayableResource) {
        if (ticket != owner) return
        pendingLoad?.cancel()
        pendingLoad = scope.launch {
            transition?.join()
            if (ticket != owner) return@launch
            loaded = ticket
            prepared = false
            val callback = object : Player.Listener {
                private var ended = false
                private fun valid() = ticket == owner && ticket == loaded
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (valid() && !playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) {
                        desiredPlaying = false
                        onEvent?.invoke(RuntimeEvent.PausedBySystem(ticket))
                    }
                }
                override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                    if (valid() && reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) onEvent?.invoke(RuntimeEvent.Suppressed(ticket))
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!valid()) return
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!prepared) {
                                prepared = true
                                preparationTimeout?.cancel()
                                onEvent?.invoke(RuntimeEvent.Prepared(ticket, duration()))
                            }
                        }
                        Player.STATE_BUFFERING -> onEvent?.invoke(RuntimeEvent.Buffering(ticket))
                        Player.STATE_ENDED -> if (!ended) {
                            ended = true
                            onEvent?.invoke(RuntimeEvent.Ended(ticket))
                        }
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (valid() && isPlaying) onEvent?.invoke(RuntimeEvent.Started(ticket))
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (valid()) {
                        preparationTimeout?.cancel()
                        onEvent?.invoke(RuntimeEvent.Failed(ticket, error.message ?: error.errorCodeName))
                    }
                }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    if (!valid() || reason != Player.DISCONTINUITY_REASON_SEEK) return
                    val seek = pendingSeeks.removeFirstOrNull() ?: return
                    // Media3 reports each seek discontinuity on its application thread.
                    ended = false
                    onEvent?.invoke(RuntimeEvent.SeekSettled(ticket, seek.first, newPosition.positionMs))
                }
            }
            listener = callback
            player.addListener(callback)
            player.playWhenReady = false
            player.volume = 0f
            player.setMediaItem(itemFactory(ticket, resource))
            player.prepare()
            preparationTimeout = scope.launch {
                delay(30_000)
                if (ticket == owner && !prepared) onEvent?.invoke(RuntimeEvent.Failed(ticket, "Media preparation timed out"))
            }
        }
    }

    override fun setPlaying(ticket: LoadTicket, playing: Boolean, smooth: Boolean) {
        if (ticket != owner) return
        desiredPlaying = playing
        if (ticket != loaded || !prepared) return
        fade?.cancel()
        if (playing) {
            if (!smooth) player.volume = gain
            player.play()
            // play() is a no-op if already playing, but a completed seek still needs a fact.
            if (player.isPlaying) onEvent?.invoke(RuntimeEvent.Started(ticket))
            if (smooth) fade = scope.launch { ramp(gain) }
        } else {
            fade = scope.launch {
                ramp(0f)
                if (ticket == owner && !desiredPlaying) player.pause()
            }
        }
    }

    override fun seek(ticket: LoadTicket, seekId: Long, positionMs: Long) {
        if (ticket != owner || ticket != loaded || !prepared) return
        if (player.currentPosition == positionMs) {
            onEvent?.invoke(RuntimeEvent.SeekSettled(ticket, seekId, player.currentPosition))
        } else {
            pendingSeeks.addLast(seekId to positionMs)
            player.seekTo(positionMs)
        }
    }

    private fun duration() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
    private suspend fun ramp(target: Float) {
        val initial = player.volume
        for (step in 1..5) {
            delay(12)
            player.volume = initial + (target - initial) * step / 5f
        }
    }

    override fun release() {
        onEvent = null
        scope.cancel()
        player.release()
    }
}
