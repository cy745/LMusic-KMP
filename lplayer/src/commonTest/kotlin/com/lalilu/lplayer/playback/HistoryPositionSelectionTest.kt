package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryPositionSelectionTest {
    private val a = LAudio(id = "42", mediaSourceName = "local")
    private val b = a.copy(mediaSourceName = "remote")

    @Test fun matchingNativeQueueCanSupplyPosition() {
        assertTrue(matchesNativeHistorySelection(QueueState(listOf(a, b), 1), listOf(a.playbackId, b.playbackId), 1))
    }

    @Test fun sameRawIdFromAnotherSourceCannotSupplyPosition() {
        assertFalse(matchesNativeHistorySelection(QueueState(listOf(a), 0), listOf(b.playbackId), 0))
    }

    @Test fun anotherOccurrenceOfTheSameSongCannotSupplyPosition() {
        assertFalse(matchesNativeHistorySelection(QueueState(listOf(a, a), 1), listOf(a.playbackId, a.playbackId), 0))
    }

    @Test fun nativeQueueStillApplyingAnEditCannotSupplyPosition() {
        assertFalse(matchesNativeHistorySelection(QueueState(listOf(a, b), 0), listOf(a.playbackId), 0))
        assertFalse(matchesNativeHistorySelection(QueueState(listOf(a, b), 0), listOf(b.playbackId, a.playbackId), 0))
    }

    @Test fun anEmptyQueueCannotSupplyPosition() {
        assertFalse(matchesNativeHistorySelection(QueueState(emptyList(), 0), emptyList(), 0))
    }
}
