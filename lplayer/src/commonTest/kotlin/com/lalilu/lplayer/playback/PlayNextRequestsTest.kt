package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.MediaKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayNextRequestsTest {
    private val a = MediaKey("local", "a")
    private val b = MediaKey("local", "b")

    @Test fun newestExplicitRequestPlaysFirstAndIsConsumedOnce() {
        val requests = PlayNextRequests()
        requests.enqueue(a)
        requests.enqueue(b)
        assertEquals(b, requests.take(setOf(a, b)))
        assertEquals(a, requests.take(setOf(a, b)))
        assertNull(requests.take(setOf(a, b)))
    }

    @Test fun repeatingRequestMovesItToFrontWithoutDuplicating() {
        val requests = PlayNextRequests()
        requests.enqueue(a)
        requests.enqueue(b)
        requests.enqueue(a)
        assertEquals(a, requests.take(setOf(a, b)))
        assertEquals(b, requests.take(setOf(a, b)))
        assertNull(requests.take(setOf(a, b)))
    }

    @Test fun removedSongIsNotReplacedBySameIdFromAnotherSource() {
        val requests = PlayNextRequests()
        requests.enqueue(a)
        assertNull(requests.take(setOf(MediaKey("remote", "a"))))
    }

    @Test fun clearDiscardsOldRequests() {
        val requests = PlayNextRequests()
        requests.enqueue(a)
        requests.clear()
        assertNull(requests.take(setOf(a)))
    }

    @Test fun removedThenReaddedSongDoesNotReviveOldRequest() {
        val requests = PlayNextRequests()
        requests.enqueue(a)
        requests.enqueue(b)
        requests.retain(setOf(b))
        requests.retain(setOf(a, b))
        assertEquals(b, requests.take(setOf(a, b)))
        assertNull(requests.take(setOf(a, b)))
    }
}
