package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lplayer.action.QueueAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertNull

/** Exercises the real AbstractPlayback queue boundary, with only the audio device replaced. */
@OptIn(ExperimentalCoroutinesApi::class)
class QueuePlaybackTest {
    private val a = LAudio(id = "a", mediaSourceName = "local")
    private val b = LAudio(id = "b", mediaSourceName = "sandbox")
    private val c = LAudio(id = "c", mediaSourceName = "local")

    @Test fun historySampleRejectsQueueReplacementDuringTheNativeRead() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a), 0, false)
        player.historyPositionReader = {
            player.queue.update { replaceAll(listOf(b), 0) }
            42_000L
        }
        assertNull(player.sampleHistoryPosition())
        assertEquals(b, player.queue.currentItem())
    }

    @Test fun unavailableNativePositionDoesNotFallBackToTheUiPosition() = runTest {
        val storage = MemoryHistory().apply { savePosition(12_000L) }
        val player = DevicePlayback(backgroundScope, storage)
        player.historyPositionReader = { null }
        runCurrent()
        assertEquals(0L, player.currentPosition())
        advanceTimeBy(2_100)
        runCurrent()
        assertEquals(12_000L, storage.savedPosition())
    }

    @Test fun failedExternalSelectionEscapesAndReleasesQueueBoundaryForRetry() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.playAudio(a)
        val failure = IllegalStateException("native load rejected")
        player.nextLoadFailure = failure
        val caught = assertFailsWith<IllegalStateException> { player.playAudio(b) }
        assertSame(failure, caught)
        assertSame(failure, player.reportedFailures.single())
        // Failure propagation is not rollback: selection has already updated the queue.
        // Retrying must release the mutex, select the existing occurrence, and load it.
        player.playAudio(b)
        assertEquals(listOf(a, b), player.queue.stateSnapshot().list)
        assertEquals(b, player.loaded)
        assertTrue(player.isPlaying.value)
    }

    @Test fun commonHistoryHookLoadsSelectedSongAndPositionWithoutStartingPlayback() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.queue.update { replaceAll(listOf(a, b), 1) }
        player.onQueueRestored(PlaybackHistory.HistorySnapshot(listOf("a", "b"), 1, 12_000))
        assertEquals(b, player.loaded)
        assertEquals(12_000L, player.currentPosition())
        assertFalse(player.isPlaying.value)
    }

    @Test fun pausedSeekIsPersistedWithoutAPlayStateTransition() = runTest {
        val storage = MemoryHistory()
        val player = DevicePlayback(backgroundScope, storage)
        runCurrent()
        player.updatePlaylist(listOf(a), 0, false)
        player.seekTo(42_000L)
        advanceTimeBy(1100)
        runCurrent()
        assertFalse(player.isPlaying.value)
        assertEquals(42_000L, storage.savedPosition())
    }

    @Test fun externalOpenInsertsOnceAndSelectsBySourceQualifiedIdentity() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.playAudio(a)
        assertEquals(a, player.loaded)
        player.playAudio(b)
        assertEquals(listOf(a, b), player.queue.stateSnapshot().list)
        player.playAudio(a)
        assertEquals(2, player.queue.stateSnapshot().list.size)
        assertEquals(a, player.loaded)
        val otherSource = a.copy(mediaSourceName = "other")
        player.playAudio(otherSource)
        assertEquals(listOf(a, otherSource, b), player.queue.stateSnapshot().list)
        assertEquals(otherSource, player.loaded)
        assertTrue(player.isPlaying.value)
    }

    @Test fun removingNonCurrentDoesNotRestartAudioOrResetPosition() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, c), 1, true)
        player.seekTo(42_000)
        QueueAction.Remove(a).execute(player)
        assertEquals(b, player.queue.currentItem())
        assertEquals(b, player.loaded)
        assertEquals(42_000, player.currentPosition())
        assertEquals(0, player.stops)
        assertTrue(player.isPlaying.value)
    }

    @Test fun removingCurrentLoadsSuccessorAndPreservesPlayIntent() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, c), 1, true)
        QueueAction.Remove(b).execute(player)
        assertEquals(c, player.loaded)
        assertEquals(1, player.stops)
        assertTrue(player.isPlaying.value)

        player.pause()
        QueueAction.Remove(c).execute(player)
        assertEquals(a, player.loaded)
        assertFalse(player.isPlaying.value)
    }

    @Test fun clearStopsDeviceAndLaterAppendProducesValidCurrent() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a), 0, true)
        QueueAction.Clear.execute(player)
        assertEquals(null, player.loaded)
        assertTrue(player.queue.stateSnapshot().list.isEmpty())
        assertFalse(player.isPlaying.value)
        QueueAction.AddToStart(b).execute(player)
        assertEquals(b, player.loaded)
        assertFalse(player.isPlaying.value)
    }

    @Test fun replacementAtSameIndexLoadsNewCurrentAndEmptyReplacementStops() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a), 0, true)
        QueueAction.Replace(0, b).execute(player)
        assertEquals(b, player.loaded)
        player.updatePlaylist(emptyList(), -1, false)
        assertEquals(null, player.loaded)
        assertEquals(0, player.queue.stateSnapshot().index)
    }

    @Test fun moveAndInsertBeforeCurrentDoNotReloadDevice() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b), 1, true)
        QueueAction.AddToPrevious(c).execute(player)
        assertEquals(listOf(a, c, b), player.queue.stateSnapshot().list)
        QueueAction.Move(2, 0).execute(player)
        assertEquals(b, player.queue.currentItem())
        assertEquals(0, player.stops)
        assertEquals(1, player.loads)
    }

    @Test fun surpriseNavigationAndLeavingShuffleKeepVisibleHistory() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        val items = (0..19).map { a.copy(id = "$it") }
        player.updatePlaylist(items, 10, true)
        player.setPlaybackMode(PlaybackMode.SHUFFLE)
        assertEquals(items[10], player.queue.currentItem())
        player.skipToNext()
        assertEquals(items[10], player.queue.stateSnapshot().rearrange()[1])
        player.skipToPrevious()
        assertEquals(items[10], player.queue.currentItem())
        val before = player.queue.stateSnapshot()
        player.setPlaybackMode(PlaybackMode.LOOP)
        assertEquals(before, player.queue.stateSnapshot())
    }

    private class DevicePlayback(scope: CoroutineScope, storage: MemoryHistory = MemoryHistory()) : AbstractPlayback(
        coroutineScope = scope,
        history = PlaybackHistoryImpl(storage),
        audioRepository = object : AudioRepository {
            override fun getAudios() = flowOf(emptyList<LAudio>())
            override fun getAudios(ids: List<String>) = getAudios()
            override fun getAudio(id: String) = flowOf<LAudio?>(null)
            override suspend fun clearUnavailableAudio() = Unit
        },
    ), HistoryPositionProvider {
        init { startHistoryPlayback() }
        var historyPositionReader: suspend () -> Long? = { currentPosition() }
        override suspend fun historyPosition(expectedQueue: QueueState): Long? = historyPositionReader()
        var loaded: LAudio? = null
        var stops = 0
        var loads = 0
        var nextLoadFailure: Exception? = null
        val reportedFailures = mutableListOf<Exception>()
        private var position = 0L
        override fun createEngines() = emptyList<PlaybackEngine>()
        override suspend fun skipTo(index: Int, start: Boolean) {
            nextLoadFailure?.let { failure ->
                nextLoadFailure = null
                reportPlaybackCommandFailure(failure) { reportedFailures += it }
            }
            loaded = queue.stateSnapshot().list[index]
            loads++
            position = 0L
            queue.update { switchTo(index) }
            _isPlaying.value = start
        }
        override suspend fun play() { _isPlaying.value = true }
        override suspend fun pause() { _isPlaying.value = false }
        override suspend fun stop() { stops++; loaded = null; _isPlaying.value = false }
        override suspend fun seekTo(positionMs: Long) { position = positionMs }
        override fun currentPosition() = position
    }

    private class MemoryHistory : HistoryStorage {
        private var position = 0L
        override fun savedPlaylistIds() = emptyList<String>()
        override fun savedPlayId() = ""
        override fun savedPosition() = position
        override fun savePlaylistIds(ids: List<String>) = Unit
        override fun savePlayId(id: String) = Unit
        override fun savePosition(position: Long) { this.position = position }
    }
}
