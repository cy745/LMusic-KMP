package com.lalilu.lplayer.action

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.lalilu.lplayer.playback.reportPlaybackCommandFailure
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerActionExecutionTest {
    @Test fun reportedPlatformFailureDoesNotRunTheSuccessContinuation() = runTest {
        val failure = IllegalStateException("decoder rejected media")
        val platformErrors = mutableListOf<Exception>()
        val actionErrors = mutableListOf<Exception>()
        var committed = false
        suspend fun command() {
            reportPlaybackCommandFailure(failure) { platformErrors += it }
        }
        launchPlayerAction({ actionErrors += it }) {
            command()
            committed = true
        }.join()
        assertFalse(committed)
        assertSame(failure, platformErrors.single())
        assertSame(failure, actionErrors.single())
        var retried = false
        launchPlayerAction({ actionErrors += it }) { retried = true }.join()
        assertTrue(retried)
    }

    @Test fun failureIsReportedAndDoesNotCancelSubsequentActions() = runTest {
        val errors = mutableListOf<Exception>()
        val failure = IllegalStateException("load failed")
        launchPlayerAction({ errors += it }) { throw failure }.join()
        var completed = false
        launchPlayerAction({ errors += it }) { completed = true }.join()
        assertSame(failure, errors.single())
        assertTrue(completed)
        assertTrue(coroutineContext.isActive)
    }

    @Test fun cancellationDoesNotReportMediaFailureOrContinueAction() = runTest {
        var reported = false
        var continued = false
        val job = launchPlayerAction({ reported = true }) {
            delay(Long.MAX_VALUE)
            continued = true
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(reported)
        assertFalse(continued)
    }

    @Test fun cancellingOwnerCancelsInFlightActions() = runTest {
        val parent = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + parent)
        var cleaned = false
        val job = scope.launchPlayerAction({ error("Cancellation must not report a failure") }) {
            try { awaitCancellation() } finally { cleaned = true }
        }
        runCurrent()
        parent.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(cleaned)
    }
}
