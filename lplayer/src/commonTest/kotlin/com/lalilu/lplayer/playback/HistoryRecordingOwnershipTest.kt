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

    @Test fun fallbackClearAppliesWhileThePhaseStillOwnsTheRecording() {
        assertTrue(shouldClearFallbackPosition(queue, queue, fallback, 3L, 3L))
    }

    @Test fun anEqualLookingReplacementOfThePhaseStillClears() {
        // StateFlow 会合并等值通知：恢复阶段被换成结构相等的新实例时，这次清零不能整体丢掉。
        val replaced = fallback.copy(pendingIds = emptySet())
        assertTrue(shouldClearFallbackPosition(queue, queue, replaced, 0L, 0L))
        assertTrue(isFallbackClearPhase(replaced))
    }

    @Test fun olderPositionSampleWinsOverALateFallbackClear() {
        // The sampler persisted a position for the selection that took over after the notification,
        // so the queued clear no longer describes the stored progress.
        assertFalse(shouldClearFallbackPosition(queue, queue, fallback, 3L, 4L))
    }

    @Test fun replacedQueueOrFinishedPhaseRejectsTheFallbackClear() {
        val replaced = queue.copy(list = queue.list + LAudio(id = "b", mediaSourceName = "local"), index = 1)
        assertFalse(shouldClearFallbackPosition(queue, replaced, fallback, 0L, 0L))
        assertFalse(shouldClearFallbackPosition(queue, queue, HistoryRestoreState.Cancelled, 0L, 0L))
        // 历史 current 已解析：这时进度属于真正的历史歌曲，不能清零。
        assertFalse(shouldClearFallbackPosition(queue, queue,
            HistoryRestoreState.Pending(originalIds = listOf("a"), currentId = "a", currentRestored = true), 0L, 0L))
        assertFalse(shouldClearFallbackPosition(queue, queue, null, 0L, 0L))
        assertFalse(isFallbackClearPhase(HistoryRestoreState.Complete(listOf("a"))))
    }
}
