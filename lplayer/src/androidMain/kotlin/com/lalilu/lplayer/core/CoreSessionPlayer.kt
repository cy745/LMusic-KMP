package com.lalilu.lplayer.core

import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.future

/** MediaSession projects the full core queue. ExoPlayer holds only the current item and one private, core-authorized successor. */
@UnstableApi
class CoreSessionPlayer(
    val core: PlaybackCore,
    private val runtime: Media3CoreRuntime,
    private val scope: CoroutineScope,
    private val items: MutableMap<TrackKey, MediaItem>,
    private val onRelease: () -> Unit = {},
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val observation = scope.launch { core.state.collect { invalidateState() } }
    private var reportedEpoch = -1L
    private var repeat = REPEAT_MODE_ALL
    private var shuffle = false
    private fun key(item: MediaItem): TrackKey {
        val identity = requireNotNull(MediaKey.parse(item.mediaId)) { "MediaSession needs source-qualified identity" }
        return TrackKey(identity.sourceName, identity.id)
    }
    private fun currentItems() = core.state.value.queue.map { items.getValue(it) }
    private fun task(block: suspend () -> Unit): ListenableFuture<Unit> = scope.future { block() }
    private suspend fun replace(list: List<MediaItem>, selected: TrackKey? = null, startPlayback: Boolean = false, force: Boolean = false) {
        list.forEach { items[key(it)] = it }
        val keys = list.map(::key).distinct()
        if (force || startPlayback || keys != core.state.value.queue || (selected != null && selected != core.state.value.selected)) {
            core.dispatch(PlaybackCommand.ReplaceQueue(keys, selected, startPlayback))
        }
        invalidateState()
    }
    override fun getState(): State {
        val s = core.state.value
        val playlist = s.queue.map { key ->
            MediaItemData.Builder(key).setMediaItem(items.getValue(key)).setIsSeekable(true)
                .setDurationUs(if (s.selected == key && s.durationMs > 0) s.durationMs * 1000 else C.TIME_UNSET).build()
        }
        return State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(
                COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_RELEASE,
                COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_METADATA,
                COMMAND_SET_MEDIA_ITEM, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SET_REPEAT_MODE, COMMAND_SET_SHUFFLE_MODE, COMMAND_GET_AUDIO_ATTRIBUTES, COMMAND_SET_AUDIO_ATTRIBUTES,
            ).build())
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(if (playlist.isEmpty()) C.INDEX_UNSET else s.index)
            .setContentPositionMs(s.positionMs)
            .setContentBufferedPositionMs { s.bufferedPositionMs }
            .setTotalBufferedDurationMs { (s.bufferedPositionMs - s.positionMs).coerceAtLeast(0) }
            .setPlayWhenReady(s.intent == PlayIntent.Play, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(when(s.phase) {
                Phase.Empty, Phase.Failed -> STATE_IDLE
                Phase.Ended -> STATE_ENDED
                Phase.Preparing, Phase.Starting, Phase.Seeking, Phase.Buffering -> STATE_BUFFERING
                else -> STATE_READY
            })
            .setPlaybackSuppressionReason(if (s.phase == Phase.Suppressed) PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS else PLAYBACK_SUPPRESSION_REASON_NONE)
            .setIsLoading(s.phase in setOf(Phase.Preparing, Phase.Buffering, Phase.Seeking))
            .setPlayerError(if (s.phase == Phase.Failed) PlaybackException(s.error ?: "Content unavailable",null,PlaybackException.ERROR_CODE_IO_UNSPECIFIED) else null)
            .setShuffleModeEnabled(s.mode == PlayMode.Random)
            .setRepeatMode(when(s.mode) { PlayMode.Sequential -> REPEAT_MODE_OFF; PlayMode.RepeatOne -> REPEAT_MODE_ONE; else -> REPEAT_MODE_ALL })
            .setAudioAttributes(runtime.player.audioAttributes)
            .setAudioSessionId(runtime.player.audioSessionId)
            .apply {
                if (reportedEpoch != s.epoch) {
                    reportedEpoch = s.epoch
                    if (s.naturalTransition && s.phase != Phase.Ended) setPositionDiscontinuity(DISCONTINUITY_REASON_AUTO_TRANSITION,s.positionMs)
                }
            }
            .build()
    }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean) = task { core.dispatch(if(playWhenReady) PlaybackCommand.Play else PlaybackCommand.Pause) }
    override fun handlePrepare() = task { /* Queue selection already prepares asynchronously, including while paused. */ }
    override fun handleStop() = task { core.dispatch(PlaybackCommand.Stop) }
    override fun handleRelease(): ListenableFuture<Unit> {
        observation.cancel()
        core.close()
        onRelease()
        return com.google.common.util.concurrent.Futures.immediateFuture(Unit)
    }
    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) = task {
        val start = mediaItems.firstOrNull()?.requestMetadata?.extras?.getBoolean(START_QUEUE_PLAYBACK) == true
        replace(mediaItems.map { it.withQueuePlaybackRequest(false) },mediaItems.getOrNull(startIndex)?.let(::key), start, force = true)
        if (startPositionMs > 0) core.dispatch(PlaybackCommand.Seek(startPositionMs))
    }
    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>) = task {
        replace(currentItems().toMutableList().apply { addAll(index.coerceIn(0,size),mediaItems) })
    }
    override fun handleReplaceMediaItems(fromIndex: Int,toIndex: Int,mediaItems: List<MediaItem>) = task {
        replace(currentItems().toMutableList().apply { subList(fromIndex,toIndex).clear(); addAll(fromIndex,mediaItems) })
    }
    override fun handleRemoveMediaItems(fromIndex: Int,toIndex: Int) = task {
        val before = core.state.value
        val next = currentItems().toMutableList().apply { subList(fromIndex,toIndex).clear() }
        val selected = if (before.index in fromIndex until toIndex) next.getOrNull(if(fromIndex < next.size) fromIndex else 0)?.let(::key) else before.selected
        replace(next,selected)
    }
    override fun handleMoveMediaItems(fromIndex: Int,toIndex: Int,newIndex: Int) = task {
        replace(currentItems().toMutableList().apply { val moved=subList(fromIndex,toIndex).toList(); subList(fromIndex,toIndex).clear(); addAll(newIndex,moved) })
    }
    override fun handleSeek(mediaItemIndex: Int,positionMs: Long,seekCommand: Int) = task {
        when(seekCommand) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> core.dispatch(PlaybackCommand.Next)
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> core.dispatch(PlaybackCommand.Previous)
            COMMAND_SEEK_TO_MEDIA_ITEM -> {
                core.state.value.queue.getOrNull(mediaItemIndex)?.let { core.dispatch(PlaybackCommand.Select(it)) }
                if(positionMs > 0) core.dispatch(PlaybackCommand.Seek(positionMs))
            }
            else -> core.dispatch(PlaybackCommand.Seek(positionMs.coerceAtLeast(0)))
        }
    }
    private suspend fun updateMode() {
        core.dispatch(PlaybackCommand.SetMode(when { repeat==REPEAT_MODE_ONE -> PlayMode.RepeatOne; shuffle -> PlayMode.Random; repeat==REPEAT_MODE_OFF -> PlayMode.Sequential; else -> PlayMode.Loop }))
    }
    override fun handleSetRepeatMode(repeatMode: Int) = task { repeat=repeatMode; updateMode() }
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean) = task { shuffle=shuffleModeEnabled; updateMode() }
    fun setMode(mode: com.lalilu.lplayer.extensions.PlayMode) = task {
        shuffle = mode == com.lalilu.lplayer.extensions.PlayMode.Shuffle
        repeat = when(mode) {
            com.lalilu.lplayer.extensions.PlayMode.RepeatOne -> REPEAT_MODE_ONE
            com.lalilu.lplayer.extensions.PlayMode.Sequential -> REPEAT_MODE_OFF
            else -> REPEAT_MODE_ALL
        }
        updateMode()
    }
    override fun handleSetAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) = task {
        runtime.player.setAudioAttributes(audioAttributes,handleAudioFocus); invalidateState()
    }
    fun queuedItem(playbackId: String): MediaItem? = currentItems().firstOrNull { it.mediaId == playbackId }
    fun requestSelection(item: MediaItem, start: Boolean) = task {
        items[key(item)] = item
        core.dispatch(PlaybackCommand.Select(key(item), startPlayback = start))
    }
    fun requestPlayNext(item: MediaItem) = task { items[key(item)]=item; core.dispatch(PlaybackCommand.PlayNext(key(item))) }
    fun pauseAtEnd(enabled: Boolean) = task { core.dispatch(PlaybackCommand.SetPauseAtEnd(enabled)) }
}
