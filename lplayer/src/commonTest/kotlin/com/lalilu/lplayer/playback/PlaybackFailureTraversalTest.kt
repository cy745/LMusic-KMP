package com.lalilu.lplayer.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackFailureTraversalTest {
    @Test fun explicitRetryStartsFreshAfterQueueChanges() {
        val traversal = PlaybackFailureTraversal()
        traversal.observeQueue(listOf("a", "b"))
        val ticket = traversal.begin()
        assertEquals(1, traversal.next(ticket, 2, 0, PlaybackMode.LOOP) { true })
        traversal.observeQueue(listOf("b", "a"))
        assertNull(traversal.next(traversal.generation, 2, 0, PlaybackMode.LOOP) { true })
        assertEquals(1, traversal.next(traversal.begin(), 2, 0, PlaybackMode.LOOP) { true })
    }

    @Test fun internalReplacementIntermediateStatesDoNotForgetAttemptedSongs() {
        val traversal = PlaybackFailureTraversal()
        traversal.observeQueue(listOf("a", "b", "c"))
        var ticket = traversal.begin()
        assertEquals(1, traversal.next(ticket, 3, 0, PlaybackMode.LOOP) { true })
        // Media3 publishes both halves of the shuffle swap, including a temporary duplicate.
        traversal.observeQueue(listOf("c", "b", "c"))
        traversal.observeQueue(listOf("c", "b", "a"))
        ticket = traversal.generation
        assertEquals(0, traversal.next(ticket, 3, 1, PlaybackMode.LOOP) { true })
        traversal.observeQueue(listOf("a", "b", "a"))
        traversal.observeQueue(listOf("a", "b", "c"))
        assertNull(traversal.next(traversal.generation, 3, 2, PlaybackMode.LOOP) { true })
    }

    @Test fun removingANonCurrentSongInvalidatesTheOldPositions() {
        val traversal = PlaybackFailureTraversal()
        traversal.observeQueue(listOf("a", "b", "c"))
        val ticket = traversal.begin(PlaybackDirection.Backward)
        traversal.observeQueue(listOf("a", "c"))
        assertNull(traversal.next(ticket, 2, 1, PlaybackMode.LOOP) { true })
        assertEquals(PlaybackDirection.Backward, traversal.direction)
        assertEquals(0, traversal.next(traversal.generation, 2, 1, PlaybackMode.LOOP) { true })
    }

    @Test fun metadataRefreshDoesNotResetTheFailureBudget() {
        val traversal = PlaybackFailureTraversal()
        traversal.observeQueue(listOf("a", "b"))
        val ticket = traversal.begin()
        assertEquals(1, traversal.next(ticket, 2, 0, PlaybackMode.LOOP) { true })
        traversal.observeQueue(listOf("a", "b"))
        assertEquals(ticket, traversal.generation)
        assertNull(traversal.next(ticket, 2, 1, PlaybackMode.LOOP) { true })
    }

    @Test fun reorderingOrChangingDuplicateCountInvalidatesOldPositions() {
        val traversal = PlaybackFailureTraversal()
        traversal.observeQueue(listOf("a", "b", "a"))
        val beforeReorder = traversal.begin()
        traversal.observeQueue(listOf("b", "a", "a"))
        assertNull(traversal.next(beforeReorder, 3, 1, PlaybackMode.LOOP) { true })
        val beforeRemove = traversal.generation
        traversal.observeQueue(listOf("b", "a"))
        assertNull(traversal.next(beforeRemove, 2, 1, PlaybackMode.LOOP) { true })
    }

    @Test fun consecutiveFailuresVisitEachSlotAtMostOnce() {
        val traversal = PlaybackFailureTraversal()
        val ticket = traversal.begin()
        assertEquals(1, traversal.next(ticket, 3, 0, PlaybackMode.LOOP) { true })
        assertEquals(2, traversal.next(ticket, 3, 1, PlaybackMode.LOOP) { true })
        assertNull(traversal.next(ticket, 3, 2, PlaybackMode.LOOP) { true })
    }

    @Test fun backwardFailuresKeepGoingBackward() {
        val traversal = PlaybackFailureTraversal()
        val ticket = traversal.begin(PlaybackDirection.Backward)
        assertEquals(1, traversal.next(ticket, 3, 2, PlaybackMode.SEQUENTIAL) { true })
        assertEquals(0, traversal.next(ticket, 3, 1, PlaybackMode.SEQUENTIAL) { true })
        assertNull(traversal.next(ticket, 3, 0, PlaybackMode.SEQUENTIAL) { true })
    }

    @Test fun pauseOrNewSelectionInvalidatesOldFailure() {
        val traversal = PlaybackFailureTraversal()
        val ticket = traversal.begin()
        traversal.cancel()
        assertNull(traversal.next(ticket, 3, 0, PlaybackMode.LOOP) { true })
    }

    @Test fun unavailableCandidatesAreSkippedWithoutLooping() {
        val traversal = PlaybackFailureTraversal()
        val ticket = traversal.begin()
        assertEquals(3, traversal.next(ticket, 4, 0, PlaybackMode.LOOP) { it == 3 })
        assertNull(traversal.next(ticket, 4, 3, PlaybackMode.LOOP) { it == 3 })
    }
}
