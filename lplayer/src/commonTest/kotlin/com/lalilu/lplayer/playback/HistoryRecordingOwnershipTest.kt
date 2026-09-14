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
}
