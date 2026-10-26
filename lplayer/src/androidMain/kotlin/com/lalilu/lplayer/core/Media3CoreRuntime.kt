package com.lalilu.lplayer.core

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lalilu.playback.core.*
import kotlinx.coroutines.*

/** The native playlist is a bounded execution window, never the application's business queue. */
class Media3CoreRuntime(
    context: Context,
    dataSourceFactory: androidx.media3.datasource.DataSource.Factory? = null,
    private val itemFactory: (LoadTicket, PlayableResource) -> MediaItem = { ticket, resource ->
        MediaItem.Builder().setMediaId("${ticket.key.source.length}:${ticket.key.source}${ticket.key.id}").setUri(resource.uri).build()
    },
    private val gain: Float = 1f,
    renderersFactory: androidx.media3.exoplayer.RenderersFactory? = null,
) : PreloadingAudioRuntime {
    override var onEvent: ((RuntimeEvent) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val player = ExoPlayer.Builder(context.applicationContext,
        renderersFactory ?: ContinuousAudioRenderersFactory(context))
        .apply { dataSourceFactory?.let { setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(it)) } }
        .build().apply { preloadConfiguration = ExoPlayer.PreloadConfiguration(5_000_000) }
    private var owner: LoadTicket? = null
    private var loaded: LoadTicket? = null
    private var staged: NextPlan? = null
    private var awaitingConfirmation: NextPlan? = null
    private var listener: Player.Listener? = null
    private var transition: Job? = null
    private var fade: Job? = null
    private var pendingLoad: Job? = null
    private var desiredPlaying = false
    private val pendingSeeks = ArrayDeque<Long>()
    private var prepared = false
    private var preparationTimeout: Job? = null

    init {
        player.volume = gain
        scope.launch {
            while (isActive) {
                delay(100)
                val ticket = loaded ?: continue
                if (ticket == owner && prepared) onEvent?.invoke(RuntimeEvent.Position(ticket, player.currentPosition, duration(), player.bufferedPosition))
            }
        }
    }

    override fun confirmNext(plan: NextPlan) {
        if (awaitingConfirmation == plan) awaitingConfirmation = null
    }

    override fun cancelNext() {
        awaitingConfirmation?.let { plan ->
            // A policy command won the race with native boundary delivery. Stop that uncommitted
            // successor and let the core traverse from the completed item under its newest policy.
            awaitingConfirmation = null
            fade?.cancel()
            player.pause()
            player.volume = 0f
            onEvent?.invoke(RuntimeEvent.Ended(plan.from))
        }
        staged = null
        // Setting the boundary guard is synchronous on the application thread, before native removal.
        player.pauseAtEndOfMediaItems = true
        val index = player.currentMediaItemIndex
        if (index >= 0 && player.mediaItemCount > index + 1) player.removeMediaItems(index + 1, player.mediaItemCount)
    }

    override fun stageNext(plan: NextPlan, resource: PlayableResource) {
        if (plan.from != owner || plan.from != loaded || !prepared) return
        cancelNext()
        staged = plan
        player.addMediaItem(itemFactory(plan.next, resource).buildUpon().setTag(plan).build())
        player.pauseAtEndOfMediaItems = !desiredPlaying
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
        cancelNext()
        loaded = null
        prepared = false
        pendingSeeks.clear()
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
                private fun valid() = listener === this && loaded == owner
                private fun advance() {
                    val plan = staged ?: return
                    if (!valid() || plan.from != loaded || player.currentMediaItem?.localConfiguration?.tag != plan) return
                    staged = null
                    awaitingConfirmation = plan
                    loaded = plan.next
                    owner = plan.next
                    ended = false
                    prepared = player.playbackState == Player.STATE_READY
                    pendingSeeks.clear()
                    player.pauseAtEndOfMediaItems = true
                    if (!desiredPlaying) player.pause()
                    onEvent?.invoke(RuntimeEvent.Transitioned(plan, duration(), player.currentPosition, player.isPlaying))
                    // Retire only the consumed prefix. The continuous sink and current decoder remain alive.
                    player.removeMediaItems(0, player.currentMediaItemIndex)
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) advance()
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!valid()) return
                    val active = loaded ?: return
                    if (!playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) {
                        desiredPlaying = false
                        onEvent?.invoke(RuntimeEvent.PausedBySystem(active))
                    } else if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && !ended) {
                        // No authorized successor (or pause-at-end): expose the actual boundary to the core.
                        ended = true
                        onEvent?.invoke(RuntimeEvent.Ended(active))
                    }
                }
                override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                    if (valid() && reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) loaded?.let { onEvent?.invoke(RuntimeEvent.Suppressed(it)) }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!valid()) return
                    advance()
                    val active = loaded ?: return
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!prepared) {
                                prepared = true
                                preparationTimeout?.cancel()
                                onEvent?.invoke(RuntimeEvent.Prepared(active, duration()))
                            }
                            if (player.isPlaying) onEvent?.invoke(RuntimeEvent.Started(active))
                        }
                        Player.STATE_BUFFERING -> onEvent?.invoke(RuntimeEvent.Buffering(active))
                        Player.STATE_ENDED -> if (!ended) {
                            ended = true
                            onEvent?.invoke(RuntimeEvent.Ended(active))
                        }
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (valid() && isPlaying) {
                        advance()
                        loaded?.let { onEvent?.invoke(RuntimeEvent.Started(it)) }
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (valid()) {
                        advance()
                        preparationTimeout?.cancel()
                        loaded?.let { onEvent?.invoke(RuntimeEvent.Failed(it, error.message ?: error.errorCodeName)) }
                    }
                }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    if (!valid()) return
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) { advance(); return }
                    if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                    val seek = pendingSeeks.removeFirstOrNull() ?: return
                    ended = false
                    loaded?.let { onEvent?.invoke(RuntimeEvent.SeekSettled(it, seek, newPosition.positionMs)) }
                }
            }
            listener = callback
            player.addListener(callback)
            player.playWhenReady = false
            player.volume = 0f
            player.setMediaItem(itemFactory(ticket, resource).buildUpon().setTag(ticket).build())
            player.prepare()
            preparationTimeout = scope.launch {
                delay(30_000)
                if (ticket == owner && !prepared) onEvent?.invoke(RuntimeEvent.Failed(ticket, "Media preparation timed out"))
            }
        }
    }

    override fun setPlaying(ticket: LoadTicket, playing: Boolean, smooth: Boolean) {
        if (ticket != owner && ticket != awaitingConfirmation?.from) return
        desiredPlaying = playing
        player.pauseAtEndOfMediaItems = !playing || staged == null
        if ((ticket != loaded && ticket != awaitingConfirmation?.from) || !prepared) return
        fade?.cancel()
        if (playing) {
            if (!smooth) player.volume = gain
            player.play()
            if (player.isPlaying) loaded?.let { onEvent?.invoke(RuntimeEvent.Started(it)) }
            if (smooth) fade = scope.launch { ramp(gain) }
        } else if (!smooth) {
            player.pause()
            player.volume = 0f
        } else {
            fade = scope.launch {
                ramp(0f)
                if (!desiredPlaying) player.pause()
            }
        }
    }

    override fun seek(ticket: LoadTicket, seekId: Long, positionMs: Long) {
        if (ticket != owner || ticket != loaded || !prepared) return
        if (player.currentPosition == positionMs) onEvent?.invoke(RuntimeEvent.SeekSettled(ticket, seekId, player.currentPosition))
        else { pendingSeeks.addLast(seekId); player.seekTo(positionMs) }
    }

    private fun duration() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
    private suspend fun ramp(target: Float) {
        val initial = player.volume
        for (step in 1..5) { delay(12); player.volume = initial + (target - initial) * step / 5f }
    }
    override fun release() {
        onEvent = null
        scope.cancel()
        player.release()
    }
}
