package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackHistoryPersistenceTest {
    @Test
    fun partialRestoreDoesNotOverwriteMissingIdsOrCurrentItem() {
        val restore = HistoryRestoreState.Pending(
            originalIds = listOf("a", "b", "c"),
            resolvedIds = listOf("a", "c"),
            pendingIds = setOf("b"),
            currentId = "b",
        )

        val persistence = historyQueuePersistence(
            state = QueueState(list = listOf(audio("a"), audio("c")), index = 0),
            restore = restore,
        )

        assertNull(persistence.currentId)
        assertNull(persistence.playlistIds)
    }

    @Test
    fun completedOrUserQueueIsPersistedNormally() {
        val state = QueueState(list = listOf(audio("x"), audio("y")), index = 1)

        val persistence = historyQueuePersistence(state, restore = null)

        assertEquals("y", persistence.currentId)
        assertEquals(listOf("x", "y"), persistence.playlistIds)
    }

    @Test
    fun fallbackPersistsResolvedCurrentButKeepsPendingHistoryIds() {
        val restore = HistoryRestoreState.Pending(
            originalIds = listOf("a", "b"),
            resolvedIds = listOf("a"),
            pendingIds = setOf("b"),
            currentId = null,
            currentRestored = true,
        )

        val persistence = historyQueuePersistence(
            state = QueueState(list = listOf(audio("a")), index = 0),
            restore = restore,
        )

        assertEquals("a", persistence.currentId)
        assertNull(persistence.playlistIds)
    }

    @Test
    fun positionIsPreservedUntilHistoricalCurrentIsApplied() {
        val pending = HistoryRestoreState.Pending(
            originalIds = listOf("a", "b"),
            resolvedIds = listOf("a"),
            pendingIds = setOf("b"),
            currentId = "b",
            currentRestored = false,
        )

        assertFalse(canPersistPlaybackPosition(pending))
        assertTrue(canPersistPlaybackPosition(pending.copy(currentRestored = true)))
        assertTrue(canPersistPlaybackPosition(HistoryRestoreState.Cancelled))
        assertTrue(canPersistPlaybackPosition(null))
    }

    private fun audio(id: String) = LAudio(id = id, title = id)
}
