package com.lalilu.lplayer.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlaybackTraversalTest {
    @Test fun sequentialFailureKeepsDirectionAndStopsAtBoundary() {
        assertEquals(listOf(3, 4), fallbackPlaybackIndices(5, 2, PlaybackMode.SEQUENTIAL, PlaybackDirection.Forward))
        assertEquals(listOf(1, 0), fallbackPlaybackIndices(5, 2, PlaybackMode.SEQUENTIAL, PlaybackDirection.Backward))
    }

    @Test fun loopingFailureWrapsButNeverRetriesFailedSlot() {
        assertEquals(listOf(3, 0, 1), fallbackPlaybackIndices(4, 2, PlaybackMode.LOOP, PlaybackDirection.Forward))
        assertEquals(listOf(1, 0, 3), fallbackPlaybackIndices(4, 2, PlaybackMode.LOOP, PlaybackDirection.Backward))
    }

    @Test fun shuffleUsesReverseCarouselDirection() {
        assertEquals(listOf(1, 0, 3), fallbackPlaybackIndices(4, 2, PlaybackMode.SHUFFLE, PlaybackDirection.Forward))
        assertEquals(listOf(3, 0, 1), fallbackPlaybackIndices(4, 2, PlaybackMode.SHUFFLE, PlaybackDirection.Backward))
    }

    @Test fun singleRepeatDoesNotLoopOnPlaybackFailure() {
        assertEquals(listOf(2), fallbackPlaybackIndices(3, 1, PlaybackMode.SINGLE_LOOP, PlaybackDirection.Forward))
        assertEquals(emptyList(), fallbackPlaybackIndices(3, 2, PlaybackMode.SINGLE_LOOP, PlaybackDirection.Forward))
    }

    @Test fun traversalIsBoundedForAllModesAndQueueSizes() {
        for (size in 0..20) for (current in -1..size) {
            for (mode in PlaybackMode.entries) for (direction in PlaybackDirection.entries) {
                val result = fallbackPlaybackIndices(size, current, mode, direction)
                assertEquals(result.distinct(), result)
                assertFalse(current in result)
                assertFalse(result.any { it !in 0 until size })
            }
        }
    }
}
