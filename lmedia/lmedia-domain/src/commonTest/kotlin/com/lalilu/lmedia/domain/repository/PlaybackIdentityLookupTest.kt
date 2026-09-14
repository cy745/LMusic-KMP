package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.fake.FakeAudioRepository
import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackIdentityLookupTest {
    private val local = LAudio(id = "42", mediaSourceName = "local")
    private val sandbox = local.copy(mediaSourceName = "sandbox")

    @Test fun sameRawIdNeverSelectsAnotherSource() = runTest {
        val repository = FakeAudioRepository().apply { seed(local, sandbox) }
        assertEquals(sandbox, repository.getAudioByPlaybackId(sandbox.playbackId).first())
        repository.seed(local)
        assertNull(repository.getAudioByPlaybackId(sandbox.playbackId).first())
    }

    @Test fun batchRetainsOrderDuplicatesAndMissingSlots() = runTest {
        val repository = FakeAudioRepository().apply { seed(local, sandbox) }
        val missing = local.copy(id = "missing")
        assertEquals(listOf(sandbox, null, local, sandbox, null), repository.getPlaybackSlots(
            listOf(sandbox.playbackId, missing.playbackId, local.playbackId, sandbox.playbackId, "invalid")
        ).first())
    }

    @Test fun ambiguousRowsAreRejectedInsteadOfPickingFirst() = runTest {
        val repository = FakeAudioRepository().apply { seed(local, local.copy(title = "duplicate")) }
        assertNull(repository.getAudioByPlaybackId(local.playbackId).first())
        assertEquals(listOf(null), repository.getPlaybackSlots(listOf(local.playbackId)).first())
    }

    @Test fun invalidAndEmptyRequestsDoNotResolveRawIds() = runTest {
        val repository = FakeAudioRepository().apply { seed(local) }
        assertNull(repository.getAudioByPlaybackId(local.id).first())
        assertEquals(emptyList(), repository.getPlaybackSlots(emptyList()).first())
    }

    @Test fun exactLookupUsesQualifiedRepositoryEntryPointWithoutRawIdQueries() = runTest {
        val requests = mutableListOf<List<String>>()
        val repository = object : AudioRepository {
            override fun getAudios() = error("Whole-library lookup is not allowed")
            override fun getAudios(ids: List<String>) = error("Raw-ID lookup is not allowed")
            override fun getAudio(id: String) = error("Raw-ID lookup is not allowed")
            override suspend fun clearUnavailableAudio() = Unit
            override fun getAudiosByPlaybackIds(playbackIds: List<String>) = flowOf(
                listOf(local, sandbox).filter { it.playbackId in playbackIds }
            ).also { requests += playbackIds }
        }
        assertEquals(sandbox, repository.getAudioByPlaybackId(sandbox.playbackId).first())
        assertEquals(listOf(sandbox, local, sandbox, null), repository.getPlaybackSlots(
            listOf(sandbox.playbackId, local.playbackId, sandbox.playbackId, "invalid")
        ).first())
        assertEquals(listOf(listOf(sandbox.playbackId), listOf(sandbox.playbackId, local.playbackId)), requests)
    }
}
