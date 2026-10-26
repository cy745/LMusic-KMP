package com.lalilu.lmedia.content

import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCacheFillerTest {
    private val a = ContentDescriptor(MediaKey("source", "a"), "scope", "v1", 200000)
    private val b = a.copy(key = MediaKey("source", "b"))

    @Test fun bulkFillUsesMiBRequestsWithoutArtificialDelay() = runTest {
        var requests = 0
        val song = a.copy(size = 8L * 1024 * 1024)
        val cache = RangeContentCache(MemoryStorage(), RangeReader { _, _, n ->
            requests++; delay(50); ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        val filler = PlaybackCacheFiller(backgroundScope, cache)
        filler.select(song)
        advanceTimeBy(500); runCurrent()
        assertTrue(filler.isComplete(song))
        assertEquals(8, requests, "One network round trip per MiB, not per 64 KiB")
        filler.close()
    }

    @Test fun cachedPlaybackReadDoesNotWaitForOrCancelBackgroundNetwork() = runTest {
        var block = false
        var cancelled = false
        val cache = RangeContentCache(MemoryStorage(), RangeReader { _, _, n ->
            if (block) try { awaitCancellation() } finally { cancelled = true }
            ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        val foreground = cache.open(a)
        foreground.read(0, 65536)
        block = true
        val filler = PlaybackCacheFiller(backgroundScope, cache)
        filler.select(a); runCurrent()
        assertEquals(100, foreground.readCached(0, 100)!!.size)
        assertFalse(cancelled)
        filler.close(); runCurrent()
        foreground.close()
    }

    @Test fun fillsOnlySelectedSongAndReusesCompleteCache() = runTest {
        val reads = mutableListOf<ContentDescriptor>()
        val cache = RangeContentCache(MemoryStorage(), RangeReader { d, _, n -> reads += d; ByteArray(n) }, io = StandardTestDispatcher(testScheduler))
        val filler = PlaybackCacheFiller(backgroundScope, cache)
        filler.select(a)
        runCurrent()
        assertTrue(filler.isComplete(a))
        assertTrue(reads.all { it == a })
        assertFalse(filler.isComplete(b))
        val count = reads.size
        // Conflated null -> same-song selection still needs a new completion owner.
        filler.select(null)
        filler.select(a); runCurrent()
        assertTrue(filler.isComplete(a))
        assertEquals(count, reads.size)
        filler.select(b); runCurrent()
        assertTrue(filler.isComplete(b))
        assertTrue(reads.any { it == b })
        filler.close()
    }

    @Test fun foregroundSeekCancelsBlockedBackgroundAndThenFillsRemainingHoles() = runTest {
        val events = mutableListOf<String>()
        var first = true
        val cache = RangeContentCache(MemoryStorage(), RangeReader { _, offset, n ->
            if (first) {
                first = false
                try { events += "background"; awaitCancellation() }
                finally { events += "cancelled" }
            }
            events += "read:$offset"
            ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        val filler = PlaybackCacheFiller(backgroundScope, cache)
        filler.select(a); runCurrent()
        val handle = cache.open(a)
        filler.foregroundRead(a, 150000) { handle.read(150000, 1000) }
        assertEquals(listOf("background", "cancelled", "read:150000"), events.take(3))
        runCurrent()
        assertTrue(filler.isComplete(a))
        assertEquals(listOf(ByteSpan(0, a.size)), handle.cachedSpans())
        handle.close(); filler.close()
    }

    @Test fun backgroundFailureAllowsForegroundReadAndRetriesWithoutChangingSelection() = runTest {
        var first = true
        var failures = 0
        val cache = RangeContentCache(MemoryStorage(), RangeReader { _, _, n ->
            if (first) { first = false; error("Temporary background failure") }
            ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        val filler = PlaybackCacheFiller(backgroundScope, cache) { failures++ }
        filler.select(a); runCurrent()
        assertEquals(1, failures)
        val handle = cache.open(a)
        assertEquals(100, filler.foregroundRead(a, 100000) { handle.read(100000, 100) }.size)
        advanceTimeBy(1000); runCurrent()
        assertTrue(filler.isComplete(a))
        handle.close(); filler.close()
    }

    @Test fun changingSelectionCancelsOldDownloadAndDoesNotPublishItsCompletion() = runTest {
        var cancelled = false
        val cache = RangeContentCache(MemoryStorage(), RangeReader { d, _, n ->
            if (d == a) try { awaitCancellation() } finally { cancelled = true }
            ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        val filler = PlaybackCacheFiller(backgroundScope, cache)
        filler.select(a); runCurrent()
        filler.select(b); runCurrent()
        assertTrue(cancelled)
        assertFalse(filler.isComplete(a))
        assertTrue(filler.isComplete(b))
        filler.close()
    }
    @Test fun authenticationFailureParksUntilSourceRefreshInsteadOfHammeringServer() = runTest {
        var denied = true
        var requests = 0
        val cache = RangeContentCache(MemoryStorage(), RangeReader { _, _, n ->
            requests++
            if (denied) throw ContentReadFailure(ContentFailureKind.Authentication, 401, "credentials expired")
            ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        val filler = PlaybackCacheFiller(backgroundScope, cache)
        filler.select(a); runCurrent()
        advanceTimeBy(120_000); runCurrent()
        assertEquals(1, requests)
        assertFalse(filler.isComplete(a))
        filler.retry("different-source"); runCurrent()
        assertEquals(1, requests)
        denied = false
        filler.retry(a.key.sourceName); runCurrent()
        assertTrue(filler.isComplete(a))
        assertEquals(2, requests)
        filler.close()
    }

    @Test fun transientOutageBacksOffAndSuccessfulForegroundMissRestartsFill() = runTest {
        var offline = true
        val times = mutableListOf<Long>()
        val cache = RangeContentCache(MemoryStorage(), RangeReader { _, _, n ->
            times += testScheduler.currentTime
            if (offline) throw ContentReadFailure(ContentFailureKind.Temporary, 503, "offline")
            ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        val filler = PlaybackCacheFiller(backgroundScope, cache)
        filler.select(a); runCurrent()
        advanceTimeBy(7_001); runCurrent()
        assertEquals(listOf(0L, 1000L, 3000L, 7000L), times)
        offline = false
        val handle = cache.open(a)
        filler.foregroundRead(a, 150000) { handle.read(150000, 100) }
        runCurrent()
        assertTrue(filler.isComplete(a), "Do not wait out the old backoff once transport works again")
        handle.close(); filler.close()
    }

    @Test fun explicitReselectionRetriesParkedFillAndOldSourceCannotWakeNewSong() = runTest {
        var denied = true
        var attempts = 0
        val cache = RangeContentCache(MemoryStorage(), RangeReader { _, _, n ->
            attempts++
            if (denied) throw ContentReadFailure(ContentFailureKind.Authentication, 403, "forbidden")
            ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        val filler = PlaybackCacheFiller(backgroundScope, cache)
        filler.select(a); runCurrent()
        denied = false
        filler.select(a); runCurrent()
        assertTrue(filler.isComplete(a))
        filler.retry(); runCurrent()
        assertEquals(2, attempts, "A healthy complete fill is not restarted")
        filler.close()
    }

    @Test fun recoveryArrivingBeforeFailureIsParkedIsNotLost() = runTest {
        var denied = true
        var attempts = 0
        val cache = RangeContentCache(MemoryStorage(), RangeReader { _, _, n ->
            attempts++
            if (denied) throw ContentReadFailure(ContentFailureKind.Authentication, 401, "old credentials")
            ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        lateinit var filler: PlaybackCacheFiller
        filler = PlaybackCacheFiller(backgroundScope, cache) {
            // Source refresh wins the race with the old failure's transition into waiting.
            denied = false
            filler.retry(a.key.sourceName)
        }
        filler.select(a); runCurrent()
        assertTrue(filler.isComplete(a))
        assertEquals(2, attempts)
        filler.close()
    }

    @Test fun unwritableCacheParksFillButForegroundRemainsPlayableAndManualRetryRecovers() = runTest {
        val storage = MemoryStorage().apply { failWrite = true }
        var requests = 0
        val cache = RangeContentCache(storage, RangeReader { _, _, n ->
            requests++; delay(10); ByteArray(n)
        }, io = StandardTestDispatcher(testScheduler))
        val filler = PlaybackCacheFiller(backgroundScope, cache)
        filler.select(a)
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, requests, "An unwritable disk must not cause endless whole-song downloads")
        assertFalse(filler.isComplete(a))
        val handle = cache.open(a)
        assertEquals(100, filler.foregroundRead(a, 0) { handle.read(0, 100) }.size)
        filler.retry(a.key.sourceName) // Network/source changes cannot repair disk persistence.
        advanceTimeBy(1000); runCurrent()
        assertEquals(2, requests)
        storage.failWrite = false
        filler.retry(); runCurrent(); advanceTimeBy(20); runCurrent()
        assertTrue(filler.isComplete(a))
        handle.close(); filler.close(); runCurrent()
        cache.updateQuota(0)
        assertTrue(storage.names().none { it.endsWith(".bin") }, "Closed filler releases its cache pin")
    }

}
