package com.lalilu.lmedia.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException

/** Bounded transport recovery for native byte bridges. Never retries authentication or stale content. */
suspend fun <T> foregroundContentRead(read: suspend () -> T): T = withTimeout(30_000) {
    var retries = 0
    while (true) {
        try { return@withTimeout read() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: IOException) {
            if (failure.contentReadFailure()?.retryable == false || retries == 6) throw failure
            delay(minOf(8000L, 500L shl retries++))
        }
    }
    @Suppress("UNREACHABLE_CODE") error("Unreachable")
}
