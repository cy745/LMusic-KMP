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

/** Compare slots, not only the current song ID: repeated occurrences own different positions. */
internal fun matchesNativeHistorySelection(
    expected: QueueState,
    nativeIds: List<String>,
    nativeIndex: Int,
): Boolean = expected.currentItem() != null && nativeIndex == expected.index &&
    nativeIds == expected.list.map { it.playbackId }

internal suspend fun Playback.sampleHistoryPosition(): Long? {
    val expected = queue.stateSnapshot()
    val position = if (this is HistoryPositionProvider) historyPosition(expected) else currentPosition()
    // Require the same published snapshot after a dispatcher hop/native query.
    // Explicit same-slot selections carry a revision, so StateFlow cannot conflate a reload.
    return position?.takeIf { it >= 0 && queue.stateSnapshot() === expected }
}
