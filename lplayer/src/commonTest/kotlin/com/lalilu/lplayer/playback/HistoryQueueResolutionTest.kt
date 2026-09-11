package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryQueueResolutionTest {
    private val local = LAudio(id = "a", mediaSourceName = "local")
    private val remote = local.copy(mediaSourceName = "remote")

    @Test fun fallbackIdentityIsNotPersistedBeforeFallbackLoadCompletes() {
        val saved = HistoryQueueIdentity(listOf("a", "missing"), listOf("local", "remote"), 1)
        val pending = HistoryRestoreState.Pending(originalIds = saved.ids, resolvedIds = listOf("a"),
            currentId = null, currentRestored = false)
        assertEquals(saved, historyIdentityForPersistence(QueueState(listOf(local), 0), pending, saved))
    }

    @Test fun v2RestoreRetainsExactIndexSourcesAndPosition() {
        val storage = MemoryStorage(HistoryQueueIdentity(listOf("a", "a"), listOf("local", "remote"), 1))
        val snapshot = PlaybackHistoryImpl(storage).restoreFromHistory()!!
        assertEquals(1, snapshot.index)
        assertEquals(listOf("local", "remote"), snapshot.sourceNames)
        assertEquals(42_000L, snapshot.position)
    }

    @Test fun invalidV2FallsBackToLegacyHistory() {
        val storage = MemoryStorage(HistoryQueueIdentity(listOf("a"), emptyList(), 9))
        val snapshot = PlaybackHistoryImpl(storage).restoreFromHistory()!!
        assertEquals(listOf("legacy"), snapshot.ids)
        assertEquals(listOf(null), snapshot.sourceNames)
    }

    private class MemoryStorage(private val identity: HistoryQueueIdentity?) : HistoryStorage {
        override fun savedQueueIdentity() = identity
        override fun savedPlaylistIds() = listOf("legacy")
        override fun savedPlayId() = "legacy"
        override fun savedPosition() = 42_000L
        override fun savePlaylistIds(ids: List<String>) = Unit
        override fun savePlayId(id: String) = Unit
        override fun savePosition(position: Long) = Unit
    }

    @Test fun fullPersistenceKeepsSourcesAndDuplicateOccurrence() {
        val identity = historyIdentityForPersistence(QueueState(listOf(local, remote, local), 2), null, null)
        assertEquals(listOf("local", "remote", "local"), identity.sourceNames)
        assertEquals(2, identity.index)
    }

    @Test fun partialPersistencePreservesMissingCurrentIdentity() {
        val saved = HistoryQueueIdentity(listOf("a", "missing"), listOf("local", "remote"), 1)
        val pending = HistoryRestoreState.Pending(originalIds = saved.ids, resolvedIds = listOf("a"),
            currentId = "missing", currentRestored = false)
        val identity = historyIdentityForPersistence(QueueState(listOf(local), 0), pending, saved)
        assertEquals(saved, identity)
    }

    @Test fun partialUserSelectionMapsToOriginalRepeatedSlot() {
        val saved = HistoryQueueIdentity(listOf("missing", "a", "a"), listOf("remote", "local", "local"), 0)
        val pending = HistoryRestoreState.Pending(originalIds = saved.ids, resolvedIds = listOf("a", "a"),
            currentId = null, currentRestored = true)
        val identity = historyIdentityForPersistence(QueueState(listOf(local, local), 1), pending, saved)
        assertEquals(2, identity.index)
        assertEquals(saved.ids, identity.ids)
    }

    @Test fun sourceIdentityRejectsSameIdFromAnotherSource() {
        val history = PlaybackHistory.HistorySnapshot(listOf("a"), 0, 100, listOf("remote"))
        assertNull(history.resolveQueue(listOf(local)).slots[0])
        assertEquals(listOf(remote), history.resolveQueue(listOf(local, remote)).items)
    }

    @Test fun missingRowsDoNotChangeSelectedDuplicateOccurrence() {
        val history = PlaybackHistory.HistorySnapshot(listOf("missing", "a", "a"), 2, 100,
            listOf("local", "local", "local"))
        val resolved = history.resolveQueue(listOf(local))
        assertEquals(listOf(local, local), resolved.items)
        assertEquals(1, resolved.currentIndex)
    }

    @Test fun legacyAmbiguousIdentityIsNotResolvedToAnArbitrarySource() {
        val history = PlaybackHistory.HistorySnapshot(listOf("a"), 0, 100)
        assertNull(history.resolveQueue(listOf(local, remote)).slots[0])
        assertEquals(listOf(local), history.resolveQueue(listOf(local)).items)
    }
}
