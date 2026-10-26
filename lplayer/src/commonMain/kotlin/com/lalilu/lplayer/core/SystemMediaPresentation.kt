package com.lalilu.lplayer.core

import com.lalilu.lmedia.MediaCoverRequest
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.playback.Playback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Common presentation only: system integrations never own selection or playback intent. */
data class SystemMediaSnapshot(val audio: LAudio?, val durationMs: Long, val positionMs: Long, val playing: Boolean)

fun Playback.systemMediaSnapshots(): Flow<SystemMediaSnapshot> = combine(
    queue.currentItemFlow(), stableDuration, isPlaying,
    flow { while (currentCoroutineContext().isActive) { emit(Unit); delay(500) } },
) { audio, duration, playing, _ ->
    SystemMediaSnapshot(audio, duration.coerceAtLeast(0), currentPosition().coerceAtLeast(0), playing)
}.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
fun Playback.systemArtworkRequests(sources: PlatformMediaSource?): Flow<MediaCoverRequest?> =
    queue.currentItemFlow().flatMapLatest { audio ->
        if (audio == null) flowOf(null)
        else sources?.findSource(audio.mediaSourceName)?.contentState?.map {
            MediaCoverRequest(audio, it.forItem(audio.id).generation)
        } ?: flowOf(MediaCoverRequest(audio, 0))
    }.distinctUntilChanged()
