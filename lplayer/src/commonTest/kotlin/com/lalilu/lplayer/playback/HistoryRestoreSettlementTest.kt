package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.repository.MediaLibrarySummary
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import com.lalilu.lmedia.domain.source.MediaContentState
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRestoreSettlementTest {
    @Test
    fun readySourceSettlesOnlyAfterItsSnapshotCommitFinishes() = runTest {
        val source = FakeSource("source")
        val repository = FakeBindingRepository(source)
        val settled = repository.observeHistoryRestoreSettled()
            .stateIn(backgroundScope, SharingStarted.Eagerly, false)
        runCurrent()
        assertFalse(settled.value)

        source.snapshot.value = Snapshot(revision = 3)
        source.contentState.value = MediaContentState(
            availability = MediaContentAvailability.Ready,
            generation = 3,
        )
        repository.states.value = mapOf(
            source.name to SourceStatus(
                resultRevision = 3,
                commitState = SnapshotCommitState.Committing(3),
            )
        )
        runCurrent()
        assertFalse(settled.value)

        repository.states.value = mapOf(
            source.name to SourceStatus(
                resultRevision = 3,
                commitState = SnapshotCommitState.Committed(3),
            )
        )
        runCurrent()
        assertTrue(settled.value)
    }

    @Test
    fun unavailableSourceIsTerminalWithoutDatabaseCommit() = runTest {
        val source = FakeSource("source")
        val repository = FakeBindingRepository(source)
        val settled = repository.observeHistoryRestoreSettled()
            .stateIn(backgroundScope, SharingStarted.Eagerly, false)

        source.contentState.value = MediaContentState(
            availability = MediaContentAvailability.Unavailable("not configured"),
        )
        runCurrent()

        assertTrue(settled.value)
    }

    private class FakeSource(
        override val name: String,
    ) : MediaSource {
        override val state = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
        override val snapshot = MutableStateFlow<Snapshot?>(null)
        override val contentState = MutableStateFlow(MediaContentState())
    }

    private class FakeBindingRepository(
        vararg sources: MediaSource,
    ) : MediaSourceBindingRepository {
        private val platformSources = PlatformMediaSource(sources.toList())
        override val states = MutableStateFlow<Map<String, SourceStatus>>(emptyMap())
        override val summary = MutableStateFlow(MediaLibrarySummary())

        override fun getSources(): PlatformMediaSource = platformSources
        override fun observeSource(name: String): Flow<SourceStatus?> = states.map { it[name] }
        override suspend fun startBinding() = Unit
        override suspend fun retryCommit(sourceName: String): Boolean = false
    }
}
