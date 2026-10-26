package com.lalilu.lplayer.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class HistoryPersistenceFailureTest {
    @Test fun failedWriteDoesNotPreventLaterRecording() = runTest {
        val mutex = Mutex()
        mutex.persistHistory { error("disk unavailable") }
        var saved = false
        mutex.persistHistory { saved = true }
        assertTrue(saved)
        assertFailsWith<CancellationException> { mutex.persistHistory { throw CancellationException() } }
    }
}
