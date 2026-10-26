package com.lalilu.lmedia.content

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundContentReadTest {
    @Test fun temporaryOutageRecoversButPersistentFailureIsBounded() = runTest {
        var reads = 0
        assertEquals(7, foregroundContentRead { if (++reads < 3) throw ContentReadFailure(ContentFailureKind.Temporary, message = "offline"); 7 })
        assertEquals(1500L, currentTime)
        reads = 0
        assertFailsWith<ContentReadFailure> { foregroundContentRead<Unit> { reads++; throw ContentReadFailure(ContentFailureKind.Temporary, message = "offline") } }
        assertEquals(7, reads)
    }
    @Test fun authenticationAndCancellationNeverRetry() = runTest {
        var reads = 0
        assertFailsWith<ContentReadFailure> { foregroundContentRead<Unit> { reads++; throw ContentReadFailure(ContentFailureKind.Authentication, message = "auth") } }
        assertEquals(1, reads)
        val work = launch { foregroundContentRead<Unit> { reads++; awaitCancellation() } }
        runCurrent(); work.cancelAndJoin(); advanceUntilIdle()
        assertEquals(2, reads)
    }
}
