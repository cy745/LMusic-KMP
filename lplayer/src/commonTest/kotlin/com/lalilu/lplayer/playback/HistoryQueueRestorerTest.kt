package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryQueueRestorerTest {
    @Test fun duplicateSelectionCompletesAlreadyFilledHistoryWithoutAnotherDatabaseEvent() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("a", "a"), 0, 12000), repository)
        restorer.start(backgroundScope, queue) { error("load failed") }
        repository.seed(audio("a"))
        runCurrent()
        assertIs<HistoryRestoreState.Pending>(restorer.state.value)
        queue.update { switchTo(1) }
        runCurrent()
        assertEquals(1, queue.stateSnapshot().index)
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }

    @Test fun selectingDuplicateAfterFailurePreventsRetryOfOriginalOccurrence() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        var deliveries = 0
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("a", "later", "a"), 0, 12000), repository)
        restorer.start(backgroundScope, queue) { deliveries++; error("load failed") }
        repository.seed(audio("a"))
        runCurrent()
        queue.update { switchTo(1) }
        runCurrent()
        val takenOver = assertIs<HistoryRestoreState.Pending>(restorer.state.value)
        assertEquals(null, takenOver.failure)
        assertTrue(takenOver.currentRestored)
        repository.seed(audio("a"), audio("later"))
        runCurrent()
        assertEquals(1, deliveries)
        assertEquals(2, queue.stateSnapshot().index)
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }

    @Test fun selectingAnotherOccurrenceCancelsOldPositionRestore() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        val finish = CompletableDeferred<Unit>()
        var applied = false
        var cancelled = false
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("a", "later", "a"), 0, 12000), repository)
        restorer.start(backgroundScope, queue) {
            try { finish.await(); applied = true } finally { cancelled = true }
        }
        repository.seed(audio("a"))
        runCurrent()
        queue.update { switchTo(1) }
        runCurrent()
        assertTrue(cancelled)
        finish.complete(Unit)
        repository.seed(audio("a"), audio("later"))
        runCurrent()
        assertFalse(applied)
        assertEquals(2, queue.stateSnapshot().index)
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }

    @Test fun laterFillPreservesCurrentSourceWhenIdsCollide() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        val first = audio("shared").copy(mediaSourceName = "first")
        val current = audio("shared").copy(mediaSourceName = "second")
        val later = audio("later").copy(mediaSourceName = "third")
        var deliveries = 0
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("shared", "later", "shared"), 2, 12000,
                listOf("first", "third", "second")), repository)
        restorer.start(backgroundScope, queue) { deliveries++ }
        repository.seed(first, current)
        runCurrent()
        assertEquals(1, queue.stateSnapshot().index)
        assertEquals(current, queue.currentItem())
        repository.seed(first, current, later)
        runCurrent()
        assertEquals(2, queue.stateSnapshot().index)
        assertEquals(current, queue.currentItem())
        assertEquals(1, deliveries)
    }

    @Test fun laterFillPreservesTheSelectedDuplicateOccurrence() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        var deliveries = 0
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("a", "later", "a"), 2, 12000), repository)
        restorer.start(backgroundScope, queue) { deliveries++ }
        repository.seed(audio("a"))
        runCurrent()
        assertEquals(1, queue.stateSnapshot().index)
        repository.seed(audio("a"), audio("later"))
        runCurrent()
        assertEquals(listOf("a", "later", "a"), queue.stateSnapshot().list.map { it.id })
        assertEquals(2, queue.stateSnapshot().index)
        assertEquals(1, deliveries)
    }

    @Test fun playbackTakeoverCancelsDeliveryWithoutStoppingLaterQueueFill() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        var applied = false
        var cancelled = false
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("a", "b"), 0, 12000), repository)
        restorer.start(backgroundScope, queue) {
            calls++
            try { release.await(); applied = true } finally { cancelled = true }
        }
        repository.seed(audio("a"))
        runCurrent()
        restorer.claimPlaybackControl()
        runCurrent()
        assertTrue(cancelled)
        release.complete(Unit)
        repository.seed(audio("a"), audio("b"))
        runCurrent()
        assertFalse(applied)
        assertEquals(1, calls)
        assertEquals(listOf("a", "b"), queue.stateSnapshot().list.map { it.id })
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }

    @Test fun takeoverPreventsFailedDeliveryFromRetryingOnDatabaseUpdate() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        var calls = 0
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("a", "b"), 0, 12000), repository)
        restorer.start(backgroundScope, queue) { calls++; error("native load failed") }
        repository.seed(audio("a"))
        runCurrent()
        assertEquals(1, calls)
        restorer.claimPlaybackControl()
        repository.seed(audio("a"), audio("b"))
        runCurrent()
        assertEquals(1, calls)
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }

    @Test fun takeoverBeforeResolutionAlsoSuppressesFallbackPlayback() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        val settled = MutableStateFlow(false)
        var calls = 0
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("a", "missing"), 1, 12000), repository, settled)
        restorer.start(backgroundScope, queue) { calls++ }
        restorer.claimPlaybackControl()
        repository.seed(audio("a"))
        settled.value = true
        runCurrent()
        assertEquals(0, calls)
        assertEquals(listOf("a"), queue.stateSnapshot().list.map { it.id })
        val pending = assertIs<HistoryRestoreState.Pending>(restorer.state.value)
        assertTrue(pending.currentRestored)
        assertEquals(null, pending.currentId)
    }

    @Test fun userSelectionCancelsInFlightLoadButKeepsLaterQueueFill() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        val release = CompletableDeferred<Unit>()
        var cancelled = false
        var applied = false
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("a", "b", "c"), 0, 1000), repository)
        restorer.start(backgroundScope, queue) {
            try { release.await(); applied = true } finally { cancelled = true }
        }
        repository.seed(audio("a"), audio("b"))
        runCurrent()
        queue.update { switchTo(1) }
        runCurrent()
        assertTrue(cancelled)
        release.complete(Unit)
        repository.seed(audio("a"), audio("b"), audio("c"))
        runCurrent()
        assertFalse(applied)
        assertEquals("b", queue.currentItem()?.id)
        assertEquals(3, queue.stateSnapshot().list.size)
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }

    @Test fun failedLoadDoesNotCompleteRestoreAndCanRetryAfterDatabaseRefresh() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        var attempts = 0
        val restorer = HistoryQueueRestorer(
            PlaybackHistory.HistorySnapshot(listOf("a"), 0, 1000), repository)
        restorer.start(backgroundScope, queue) {
            attempts++
            if (attempts == 1) error("load failed")
        }
        repository.seed(audio("a"))
        runCurrent()
        val failed = assertIs<HistoryRestoreState.Pending>(restorer.state.value)
        assertFalse(failed.currentRestored)
        assertEquals("load failed", failed.failure)
        repository.seed(audio("a").copy(title = "new locator"))
        runCurrent()
        assertEquals(2, attempts)
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }
    @Test
    fun partialDatabaseResultsAreFilledInOriginalOrder() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        var restoredCount = 0
        val restorer = HistoryQueueRestorer(
            snapshot = PlaybackHistory.HistorySnapshot(
                ids = listOf("a", "b", "c"),
                index = 1,
                position = 1200L,
            ),
            repository = repository,
        )
        restorer.start(backgroundScope, queue) { restoredCount++ }
        runCurrent()

        repository.seed(audio("c"), audio("a"))
        runCurrent()
        assertEquals(listOf("a", "c"), queue.stateSnapshot().list.map { it.id })
        assertEquals(0, restoredCount)
        assertEquals(setOf("b"), assertIs<HistoryRestoreState.Pending>(restorer.state.value).pendingIds)

        repository.seed(audio("c"), audio("b"), audio("a"))
        runCurrent()
        assertEquals(listOf("a", "b", "c"), queue.stateSnapshot().list.map { it.id })
        assertEquals(1, queue.stateSnapshot().index)
        assertEquals(1, restoredCount)
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }

    @Test
    fun userQueueReplacementCancelsPendingRestore() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        val restorer = HistoryQueueRestorer(
            snapshot = PlaybackHistory.HistorySnapshot(listOf("a", "b"), 0, 0L),
            repository = repository,
        )
        restorer.start(backgroundScope, queue) {}
        repository.seed(audio("a"))
        runCurrent()

        queue.update { replaceAll(listOf(audio("user")), 0) }
        runCurrent()
        repository.seed(audio("a"), audio("b"))
        runCurrent()

        assertEquals(listOf("user"), queue.stateSnapshot().list.map { it.id })
        assertIs<HistoryRestoreState.Cancelled>(restorer.state.value)
    }

    @Test
    fun duplicateIdsAndSavedOccurrenceArePreserved() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        val restorer = HistoryQueueRestorer(
            snapshot = PlaybackHistory.HistorySnapshot(
                ids = listOf("a", "a", "b"),
                index = 1,
                position = 0L,
            ),
            repository = repository,
        )
        restorer.start(backgroundScope, queue) {}

        repository.seed(audio("a"), audio("b"))
        runCurrent()

        assertEquals(listOf("a", "a", "b"), queue.stateSnapshot().list.map { it.id })
        assertEquals(1, queue.stateSnapshot().index)
    }

    @Test
    fun missingCurrentFallsBackAfterAllSourcesSettleWithoutBlockingLaterFill() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        val settled = MutableStateFlow(false)
        var restoredCount = 0
        val restorer = HistoryQueueRestorer(
            snapshot = PlaybackHistory.HistorySnapshot(
                ids = listOf("a", "b"),
                index = 1,
                position = 900L,
            ),
            repository = repository,
            restoreSettled = settled,
        )
        restorer.start(backgroundScope, queue) { restoredCount++; assertEquals(0L, it.position) }

        repository.seed(audio("a"))
        runCurrent()
        val waiting = assertIs<HistoryRestoreState.Pending>(restorer.state.value)
        assertEquals("b", waiting.currentId)
        assertEquals(false, waiting.currentRestored)

        settled.value = true
        runCurrent()
        val fallback = assertIs<HistoryRestoreState.Pending>(restorer.state.value)
        assertEquals(null, fallback.currentId)
        assertEquals(true, fallback.currentRestored)
        assertEquals(listOf("a"), queue.stateSnapshot().list.map { it.id })

        repository.seed(audio("a"), audio("b"))
        runCurrent()
        assertEquals(listOf("a", "b"), queue.stateSnapshot().list.map { it.id })
        assertEquals(0, queue.stateSnapshot().index)
        assertEquals(1, restoredCount)
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }

    @Test
    fun mediaBrowserSyncDoesNotCancelPendingRestore() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        var restoredCount = 0
        val restorer = HistoryQueueRestorer(
            snapshot = PlaybackHistory.HistorySnapshot(
                ids = listOf("a", "b"),
                index = 1,
                position = 900L,
            ),
            repository = repository,
        )
        restorer.start(backgroundScope, queue) { restoredCount++ }

        repository.seed(audio("a"))
        runCurrent()
        queue.update(updateReason = QueueUpdateReason.Sync) {
            replaceAll(emptyList(), 0)
        }
        runCurrent()
        assertIs<HistoryRestoreState.Pending>(restorer.state.value)

        repository.seed(audio("a"), audio("b"))
        runCurrent()

        assertEquals(listOf("a", "b"), queue.stateSnapshot().list.map { it.id })
        assertEquals(1, queue.stateSnapshot().index)
        assertEquals(1, restoredCount)
        assertIs<HistoryRestoreState.Complete>(restorer.state.value)
    }

    private fun audio(id: String) = LAudio(id = id, title = id)

    private class FakeAudioRepository : AudioRepository {
        private val audios = MutableStateFlow<List<LAudio>>(emptyList())

        fun seed(vararg values: LAudio) {
            audios.value = values.toList()
        }

        override fun getAudios(): Flow<List<LAudio>> = audios
        override fun getAudios(ids: List<String>): Flow<List<LAudio>> = audios.map { values ->
            values.filter { it.id in ids }
        }

        override fun getAudio(id: String): Flow<LAudio?> = audios.map { values ->
            values.firstOrNull { it.id == id }
        }

        override suspend fun clearUnavailableAudio() = Unit
    }
}
