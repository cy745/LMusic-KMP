package com.lalilu.lmedia.content

import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCacheFillerTest {
    private val a = ContentDescriptor(MediaKey("source", "a"), "scope", "v1", 200000)
    private val b = a.copy(key = MediaKey("source", "b"))

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
}
