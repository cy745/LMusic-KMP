package com.lalilu.lplayer.core

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.lalilu.lmedia.content.contentReadFailure

/** A bounded transport recovery window. Queue failure/skip policy remains in PlaybackCore. */
@UnstableApi
class ContentLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(6) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (loadErrorInfo.errorCount > 6) return C.TIME_UNSET
        if (loadErrorInfo.exception.contentReadFailure()?.retryable == false) return C.TIME_UNSET
        // Preserve Media3's fatal parser/file/position classifications.
        if (super.getRetryDelayMsFor(loadErrorInfo) == C.TIME_UNSET) return C.TIME_UNSET
        return minOf(8_000L, 500L shl (loadErrorInfo.errorCount - 1).coerceIn(0, 4))
    }
}
