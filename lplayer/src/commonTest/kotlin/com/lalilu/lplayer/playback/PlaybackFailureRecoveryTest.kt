package com.lalilu.lplayer.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackFailureRecoveryTest {
    @Test fun failedLookupPausesAndReportsWithoutEscapingCallback() = runTest {
        val failure = IllegalStateException("database unavailable")
        var reported: Exception? = null
        var pauses = 0
        runPlaybackFailureRecovery({ true }, { reported = it }, { pauses++ }) { throw failure }
        assertSame(failure, reported)
        assertEquals(1, pauses)
    }

    @Test fun successfulRecoveryDoesNotPause() = runTest {
        var recovered = 0
        runPlaybackFailureRecovery({ true }, { error("unexpected failure") }, { error("unexpected pause") }) {
            recovered++
        }
        assertEquals(1, recovered)
    }

    @Test fun staleCallbackDoesNotEvenQueryStorage() = runTest {
        runPlaybackFailureRecovery({ false }, { error("unexpected failure") }, { error("unexpected pause") }) {
            error("stale callback queried storage")
        }
    }

    @Test fun failedSuspendedLookupCannotPauseAfterUserTakeover() = runTest {
        val lookup = CompletableDeferred<Unit>()
        var current = true
        var pauses = 0
        var reports = 0
        val recovery = launch {
            runPlaybackFailureRecovery({ current }, { reports++ }, { pauses++ }) { lookup.await() }
        }
        runCurrent()
        current = false // Pause, replacement, or a newer selected occurrence owns playback now.
        lookup.completeExceptionally(IllegalStateException("database unavailable"))
        recovery.join()
        assertEquals(1, reports)
        assertEquals(0, pauses)
    }

    @Test fun cancellationIsNotReportedAsDatabaseFailureOrConvertedToPause() = runTest {
        val cancelled = CancellationException("selection replaced")
        val result = assertFailsWith<CancellationException> {
            runPlaybackFailureRecovery({ true }, { error("unexpected failure") }, { error("unexpected pause") }) {
                throw cancelled
            }
        }
        assertSame(cancelled, result)
    }
}
