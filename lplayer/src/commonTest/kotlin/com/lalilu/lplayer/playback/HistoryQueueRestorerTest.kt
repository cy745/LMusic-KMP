package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryQueueRestorerTest {
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
        restorer.start(backgroundScope, queue) { restoredCount++ }

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
        assertEquals(0, restoredCount)
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
