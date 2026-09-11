package com.lalilu.lplayer.playback

import kotlinx.coroutines.CancellationException

/** A reported error is still a failed command. Cancellation is never a media error. */
internal inline fun reportPlaybackCommandFailure(error: Exception, report: (Exception) -> Unit): Nothing {
    if (error is CancellationException) throw error
    try {
        report(error)
    } catch (reportFailure: Exception) {
        if (reportFailure is CancellationException) throw reportFailure
        if (reportFailure !== error) error.addSuppressed(reportFailure)
    }
    throw error
}
