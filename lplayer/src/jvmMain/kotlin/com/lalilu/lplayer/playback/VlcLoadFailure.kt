package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.source.AudioMediaMissingException
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Persist safe categories only; native/server exception text can include private paths or URLs. */
internal fun classifyVlcLoadFailure(error: Throwable): PlaybackFailureReason {
    val seen = mutableSetOf<Throwable>()
    var cause: Throwable? = error
    while (cause != null && seen.add(cause)) {
        when (cause) {
            is SecurityException -> return PlaybackFailureReason.PermissionDenied
            is AudioMediaMissingException, is FileNotFoundException -> return PlaybackFailureReason.FileMissing
            is ConnectException, is SocketTimeoutException, is UnknownHostException -> return PlaybackFailureReason.Network
        }
        cause = cause.cause
    }
    return PlaybackFailureReason.Unknown
}
