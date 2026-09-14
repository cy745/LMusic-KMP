package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.PlaybackFailure
import kotlinx.coroutines.flow.Flow

/** Separate from source snapshots: scanning must not erase an actual playback failure. */
interface PlaybackFailureRepository {
    val failures: Flow<Map<String, PlaybackFailure>>
    suspend fun record(playbackId: String, failure: PlaybackFailure)
    suspend fun clearAfterSuccessfulPlayback(playbackId: String)
}
