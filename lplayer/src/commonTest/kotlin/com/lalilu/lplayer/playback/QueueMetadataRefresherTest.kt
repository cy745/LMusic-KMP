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

@OptIn(ExperimentalCoroutinesApi::class)
class QueueMetadataRefresherTest {

    @Test
    fun refreshesEntitiesWithoutChangingOrderOrCurrentIndex() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(audio("a", "old a"), audio("b", "old b")), index = 1) }
        backgroundScope.startQueueMetadataRefresh(queue, repository)
        runCurrent()

        // Repository 顺序故意与队列相反，刷新后仍应保持原队列顺序。
        repository.seed(audio("b", "new b"), audio("a", "new a"))
        runCurrent()

        assertEquals(listOf("a", "b"), queue.stateSnapshot().list.map { it.id })
        assertEquals(listOf("new a", "new b"), queue.stateSnapshot().list.map { it.title })
        assertEquals(1, queue.stateSnapshot().index)
        assertEquals(QueueUpdateReason.Sync, queue.stateSnapshot().updateReason)
    }

    @Test
    fun keepsItemsThatAreTemporarilyMissingFromDatabase() = runTest {
        val repository = FakeAudioRepository()
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(audio("a", "old a"), audio("b", "old b")), index = 0) }
        backgroundScope.startQueueMetadataRefresh(queue, repository)
        runCurrent()

        repository.seed(audio("a", "new a"))
        runCurrent()

        assertEquals(listOf("new a", "old b"), queue.stateSnapshot().list.map { it.title })
    }

    @Test
    fun staleRefreshDoesNotOverwriteChangedQueue() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(listOf(audio("a", "old a")), index = 0) }
        val observedIds = listOf("a")

        queue.update { replaceAll(listOf(audio("b", "new queue")), index = 0) }
        queue.update(
            updateReason = QueueUpdateReason.Sync,
            predicate = { state -> state.list.map { it.id } == observedIds },
        ) {
            replaceAll(listOf(audio("a", "stale refresh")), index = -1)
        }

        assertEquals(listOf("b"), queue.stateSnapshot().list.map { it.id })
        assertEquals(QueueUpdateReason.Inner, queue.stateSnapshot().updateReason)
    }

    private fun audio(id: String, title: String) = LAudio(id = id, title = title)

    private class FakeAudioRepository : AudioRepository {
        private val audios = MutableStateFlow<List<LAudio>>(emptyList())

        fun seed(vararg values: LAudio) {
            audios.value = values.toList()
        }

        override fun getAudios(): Flow<List<LAudio>> = audios

        override fun getAudios(ids: List<String>): Flow<List<LAudio>> =
            audios.map { list -> list.filter { it.id in ids } }

        override fun getAudio(id: String): Flow<LAudio?> =
            audios.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun clearUnavailableAudio() = Unit
    }
}
