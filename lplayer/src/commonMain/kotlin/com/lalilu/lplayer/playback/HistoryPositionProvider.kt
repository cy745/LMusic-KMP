package com.lalilu.lplayer.playback

/**
 * Native adapters can opt in while history sampling is migrated across platforms.
 * Return null when the supplied immutable queue state no longer describes the
 * loaded selection, or while a command is still applying/loading it.
 * This does not make separately persisted queue and position keys atomic.
 */
internal interface HistoryPositionProvider {
    suspend fun historyPosition(expectedQueue: QueueState): Long?
}

internal suspend fun Playback.sampleHistoryPosition(): Long? {
    val expected = queue.stateSnapshot()
    val position = if (this is HistoryPositionProvider) historyPosition(expected) else currentPosition()
    // Require the same published snapshot after a dispatcher hop/native query.
    // This is not a request generation token: StateFlow can conflate equal values.
    return position?.takeIf { it >= 0 && queue.stateSnapshot() === expected }
}
