package com.lalilu.lmusic.external

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.repository.SourceStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExternalAudioCommitTest {
    private val target = LAudio(id = "song", mediaSourceName = "sandbox")
    @Test fun sourceQualifiedLookupConfirmsRenameDespiteAmbiguousRawId() = runTest {
        val renamed = target.copy(extra = mapOf("path" to "/new.mp3"))
        val other = target.copy(mediaSourceName = "other", extra = mapOf("path" to "/other.mp3"))
        val repository = object : AudioRepository {
            override fun getAudios() = flowOf(listOf(other, renamed))
            override fun getAudios(ids: List<String>) = error("Must use qualified lookup")
            override fun getAudio(id: String) = error("Raw ID is ambiguous")
            override fun getAudiosByPlaybackIds(playbackIds: List<String>) =
                flowOf(listOf(other, renamed).filter { it.playbackId in playbackIds })
            override suspend fun clearUnavailableAudio() = Unit
        }
        assertEquals(renamed, awaitExternalAudioCommit(target, 4, flowOf(committed(4)),
            repository.getAudioByPlaybackId(target.playbackId), expectedPath = "/new.mp3"))
    }

    private fun committed(revision: Long) = SourceStatus(
        commitState = SnapshotCommitState.Committed(revision),
    )

    @Test fun acceptsNewerCommittedRevision() = runTest {
        assertEquals(target, awaitExternalAudioCommit(target, 4, flowOf(committed(5)), flowOf(target)))
    }

    @Test fun renameWaitsForNewPathDespiteMatchingIdentityAndRevision() = runTest {
        val rows = MutableStateFlow(target.copy(extra = mapOf("path" to "/old.mp3")))
        val result = async {
            awaitExternalAudioCommit(target, 4, flowOf(committed(5)), rows, expectedPath = "/new.mp3")
        }
        runCurrent()
        assertFalse(result.isCompleted)
        rows.value = target.copy(extra = mapOf("path" to "/new.mp3"))
        assertEquals(rows.value, result.await())
    }

    @Test fun waitsForDatabaseEvenWhenCommitAlreadyCompleted() = runTest {
        val rows = MutableStateFlow<LAudio?>(null)
        val result = async { awaitExternalAudioCommit(target, 4, flowOf(committed(4)), rows) }
        runCurrent()
        assertFalse(result.isCompleted)
        rows.value = target.copy(mediaSourceName = "other")
        runCurrent()
        assertFalse(result.isCompleted)
        rows.value = target.copy(available = false)
        runCurrent()
        assertFalse(result.isCompleted)
        rows.value = target
        assertEquals(target, result.await())
    }

    @Test fun oldRevisionDoesNotConfirmNewImport() = runTest {
        val statuses = MutableStateFlow(committed(3))
        val result = async { awaitExternalAudioCommit(target, 4, statuses, flowOf(target)) }
        runCurrent()
        assertFalse(result.isCompleted)
        statuses.value = committed(5)
        assertEquals(target, result.await())
    }

    @Test fun newerFailureIsNotMistakenForSuccessfulSupersession() = runTest {
        assertFailsWith<IllegalStateException> {
            awaitExternalAudioCommit(target, 4, flowOf(SourceStatus(
                commitState = SnapshotCommitState.Failed(5, "database failure"),
            )), flowOf(target))
        }
    }

    @Test fun disabledSourceCannotBePlayedFromStaleDatabaseRow() = runTest {
        assertFailsWith<IllegalStateException> {
            awaitExternalAudioCommit(target, 4, flowOf(committed(4).copy(enabled = false)), flowOf(target))
        }
    }

    @Test fun missingTargetTimesOutInsteadOfAcceptingOtherSource() = runTest {
        assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
            awaitExternalAudioCommit(target, 4, flowOf(committed(6)),
                MutableStateFlow(target.copy(mediaSourceName = "other")), timeoutMillis = 100)
        }
    }
}
