package com.lalilu.lplayer.action

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueSelectionTest {
    private val local = LAudio(id = "42", mediaSourceName = "local")
    private val remote = local.copy(mediaSourceName = "remote")
    private val repository = object : AudioRepository {
        override fun getAudios() = flowOf(listOf(local, remote))
        override fun getAudios(ids: List<String>) = flowOf(listOf(local, remote).filter { it.id in ids })
        override fun getAudio(id: String) = error("Never use ambiguous singleton lookup")
        override suspend fun clearUnavailableAudio() = Unit
    }

    @Test fun preservesRequestedSourceOrderAndDuplicates() = runTest {
        val selected = repository.resolveQueueSelection(
            listOf(remote.playbackId, "invalid", local.playbackId, remote.playbackId), local.playbackId,
        )
        assertEquals(listOf(remote, local, remote), selected?.items)
        assertEquals(1, selected?.index)
    }

    @Test fun missingSelectedSongDoesNotFallBackToAnotherSong() = runTest {
        assertNull(repository.resolveQueueSelection(listOf(local.playbackId), remote.playbackId))
        assertNull(repository.resolveQueueSelection(listOf(local.playbackId), "42"))
    }

    @Test fun explicitEmptyQueueCanStillClearPlayback() = runTest {
        assertEquals(QueueSelection(emptyList(), 0), repository.resolveQueueSelection(emptyList(), null))
        assertNull(repository.resolveQueueSelection(listOf("missing"), null))
    }
}
