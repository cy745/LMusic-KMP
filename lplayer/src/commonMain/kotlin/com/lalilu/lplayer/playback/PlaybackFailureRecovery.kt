package com.lalilu.lplayer.playback

import kotlinx.coroutines.CancellationException

/** A failed lookup must not escape a player callback or stop a newer selection. */
internal suspend fun runPlaybackFailureRecovery(
    isCurrent: () -> Boolean,
    onFailure: (Exception) -> Unit,
    pause: () -> Unit,
    recover: suspend () -> Unit,
) {
    if (!isCurrent()) return
    try {
        recover()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onFailure(failure)
        // The lookup may have suspended while the user took control. Do not pause their song.
        if (isCurrent()) pause()
    }
}
