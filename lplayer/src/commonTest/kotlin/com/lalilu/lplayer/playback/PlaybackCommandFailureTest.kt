package com.lalilu.lplayer.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class PlaybackCommandFailureTest {
    @Test fun cancellationEscapesWithoutReportingOrContinuingCommand() {
        val cancellation = CancellationException("superseded selection")
        var reported = false
        var continued = false
        val caught = assertFailsWith<CancellationException> {
            reportPlaybackCommandFailure(cancellation) { reported = true }
            continued = true
        }
        assertSame(cancellation, caught)
        assertFalse(reported)
        assertFalse(continued)
    }

    @Test fun timeoutCancellationIsNotConvertedIntoMediaError() = runTest {
        var reported = false
        assertFailsWith<CancellationException> {
            try {
                withTimeout(100) { delay(200) }
            } catch (failure: Exception) {
                reportPlaybackCommandFailure(failure) { reported = true }
            }
        }
        assertFalse(reported)
    }

    @Test fun ordinaryFailureIsReportedAndStillFailsTheCommand() {
        val failure = IllegalStateException("bad media")
        val reports = mutableListOf<Exception>()
        val caught = assertFailsWith<IllegalStateException> {
            reportPlaybackCommandFailure(failure) { reports += it }
        }
        assertSame(failure, caught)
        assertSame(failure, reports.single())
    }

    @Test fun reporterFailureDoesNotHideTheOriginalMediaFailure() {
        val failure = IllegalStateException("load failed")
        val reportFailure = IllegalArgumentException("report failed")
        val caught = assertFailsWith<IllegalStateException> {
            reportPlaybackCommandFailure(failure) { throw reportFailure }
        }
        assertSame(failure, caught)
        assertSame(reportFailure, caught.suppressedExceptions.single())
    }

    @Test fun reporterCancellationStillCancelsTheCaller() {
        val cancellation = CancellationException("owner cancelled")
        val caught = assertFailsWith<CancellationException> {
            reportPlaybackCommandFailure(IllegalStateException("bad media")) { throw cancellation }
        }
        assertSame(cancellation, caught)
    }
}
