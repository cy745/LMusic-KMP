package com.lalilu.lplayer.core

import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lplayer.playback.IPlaybackDataTracker
import com.lalilu.lplayer.playback.ListeningObservation
import com.lalilu.playback.core.*

/** Synchronous, ordered handoff; persistence must never block the audio command loop. */
fun IPlaybackDataTracker.observeCore(state: PlaybackSnapshot, title: String?, completed: Boolean) {
    onObservation(ListeningObservation(
        state.selected?.let { MediaKey(it.source, it.id).stableKey }, title.orEmpty(), state.epoch,
        state.phase == Phase.Playing, state.durationMs, state.positionMs,
        state.phase == Phase.Seeking || state.pendingSeekId != null, completed, state.historyRequested,
    ))
}
