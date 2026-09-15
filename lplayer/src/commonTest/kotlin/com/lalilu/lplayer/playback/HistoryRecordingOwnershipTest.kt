package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryRecordingOwnershipTest {
    private val queue = QueueState(listOf(LAudio(id = "a", mediaSourceName = "local")))

    @Test fun currentNotificationMayBeSaved() {
        assertTrue(isCurrentHistoryRecording(queue, null, queue, null))
    }

    @Test fun oldQueueCannotOverwriteNewSelection() {
        val selected = queue.copy(list = queue.list + LAudio(id = "b", mediaSourceName = "local"), index = 1)
        assertFalse(isCurrentHistoryRecording(queue, null, selected, null))
    }

    @Test fun equalLookingReplacementStillInvalidatesCapturedWork() {
        assertFalse(isCurrentHistoryRecording(queue, null, queue.copy(), null))
    }

    @Test fun restorePhaseChangeInvalidatesQueuedNotification() {
        assertFalse(isCurrentHistoryRecording(queue, HistoryRestoreState.Inactive,
            queue, HistoryRestoreState.Cancelled))
        assertTrue(isCurrentHistoryRecording(queue, HistoryRestoreState.Cancelled,
            queue, HistoryRestoreState.Cancelled))
    }

    private val fallback = HistoryRestoreState.Pending(
        originalIds = listOf("a"),
        currentId = null,
        currentRestored = true,
    )

    @Test fun fallbackClearAppliesWhileItsOwnPhaseStillOwnsTheRecording() {
        assertTrue(shouldClearFallbackPosition(queue, fallback, queue, fallback, 3L, 3L))
    }

    @Test fun olderPositionSampleWinsOverALateFallbackClear() {
        // The sampler persisted a position for the selection that took over after the notification,
        // so the queued clear no longer describes the stored progress.
        assertFalse(shouldClearFallbackPosition(queue, fallback, queue, fallback, 3L, 4L))
    }

    @Test fun replacedQueueOrPhaseRejectsTheFallbackClear() {
        val replaced = queue.copy(list = queue.list + LAudio(id = "b", mediaSourceName = "local"), index = 1)
        assertFalse(shouldClearFallbackPosition(queue, fallback, replaced, fallback, 0L, 0L))
        assertFalse(shouldClearFallbackPosition(queue, fallback, queue, HistoryRestoreState.Cancelled, 0L, 0L))
        assertFalse(shouldClearFallbackPosition(queue, fallback, queue, fallback.copy(pendingIds = emptySet()), 0L, 0L))
    }
}
