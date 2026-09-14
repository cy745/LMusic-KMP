package com.lalilu.lmedia.domain.repository

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StartupSourceRecoveryTest {
    private val waiting = SourceStatus(contentAvailability = MediaContentAvailability.Preparing)
    private val ready = SourceStatus(contentAvailability = MediaContentAvailability.Ready,
        resultRevision = 1, commitState = SnapshotCommitState.Committed(1))

    @Test fun noticeWaitsForFirstDatabaseCommitButDoesNotDisableReadableCachedSongs() = runTest {
        val committing = ready.copy(commitState = SnapshotCommitState.Committing(1))
        val sources = MutableStateFlow(mapOf("source" to committing))
        val recovery = StartupSourceRecovery(sources, backgroundScope)
        runCurrent()
        assertTrue(recovery.state.value.visible)
        assertTrue(sources.value.canPlay(LAudio(mediaSourceName = "source")))
        sources.value = mapOf("source" to ready.copy(commitState = SnapshotCommitState.Failed(1, "disk")))
        runCurrent()
        assertFalse(recovery.state.value.visible)
    }

    @Test fun timeoutOnlyHidesNoticeAndNeverMarksSlowSourceReady() = runTest {
        val sources = MutableStateFlow(mapOf("slow" to waiting))
        val recovery = StartupSourceRecovery(sources, backgroundScope)
        runCurrent()
        assertTrue(recovery.state.value.visible)
        advanceTimeBy(14_000); runCurrent()
        assertEquals(1, recovery.state.value.remainingSeconds)
        advanceTimeBy(1_000); runCurrent()
        assertFalse(recovery.state.value.visible)
        assertEquals(waiting, sources.value["slow"])
        sources.value = mapOf("slow" to ready)
        runCurrent()
        assertTrue(sources.value.canPlay(LAudio(mediaSourceName = "slow")))
        assertFalse(recovery.state.value.visible)
    }

    @Test fun dismissDoesNotCancelReadinessAndNeverReappears() = runTest {
        val sources = MutableStateFlow(mapOf("slow" to waiting))
        val recovery = StartupSourceRecovery(sources, backgroundScope)
        runCurrent()
        recovery.dismiss()
        sources.value = mapOf("slow" to ready)
        runCurrent()
        sources.value = mapOf("slow" to waiting)
        runCurrent()
        assertFalse(recovery.state.value.visible)
        assertTrue(recovery.state.value.dismissed)
    }

    @Test fun readyFailedDisabledAndMissingSourcesFinishWithoutWaitingForTimeout() = runTest {
        val sources = MutableStateFlow(mapOf("a" to waiting, "b" to waiting, "c" to waiting, "d" to waiting))
        val recovery = StartupSourceRecovery(sources, backgroundScope)
        runCurrent()
        sources.value = mapOf("a" to ready, "b" to waiting.copy(enabled = false),
            "c" to waiting.copy(contentAvailability = MediaContentAvailability.Unavailable("offline")))
        runCurrent()
        assertFalse(recovery.state.value.visible)
        assertTrue(recovery.state.value.pendingSources.isEmpty())
    }

    @Test fun initialReadyAndDisabledSourcesDoNotShowNoticeOrJoinLaterRefresh() = runTest {
        val sources = MutableStateFlow(mapOf("ready" to ready, "off" to waiting.copy(enabled = false)))
        val recovery = StartupSourceRecovery(sources, backgroundScope)
        runCurrent()
        assertFalse(recovery.state.value.visible)
        sources.value = mapOf("ready" to waiting, "off" to waiting)
        runCurrent()
        assertFalse(recovery.state.value.visible)
    }

    @Test fun readinessAndDatabaseAvailabilityMustBothAllowPlayback() {
        val song = LAudio(mediaSourceName = "source")
        assertFalse(emptyMap<String, SourceStatus>().canPlay(song))
        assertFalse(mapOf("source" to waiting).canPlay(song))
        assertTrue(mapOf("source" to ready).canPlay(song))
        assertFalse(mapOf("source" to ready).canPlay(song.copy(available = false)))
        assertFalse(mapOf("source" to ready.copy(enabled = false)).canPlay(song))
        assertFalse(mapOf("source" to ready.copy(enablementChanging = true)).canPlay(song))
        assertFalse(mapOf("source" to ready.copy(enablementError = "failed")).canPlay(song))
    }
}
