package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lplayer.action.QueueAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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

    @Test fun failedDeletionRestoresDuplicatesSelectionAndPositionWithoutAutoplay() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, a, c), 2, true)
        player.seekTo(12345)
        val deletion = QueueRemovalRecovery(player, setOf(a.mediaKey))
        deletion.remove()
        assertEquals(listOf(b, c), player.queue.stateSnapshot().list)
        assertTrue(deletion.restore(listOf(a)))
        assertEquals(listOf(a, b, a, c), player.queue.stateSnapshot().list)
        assertEquals(2, player.queue.stateSnapshot().index)
        assertEquals(12345L, player.currentPosition())
        assertFalse(player.isPlaying.value)
    }

    @Test fun failedDeletionDoesNotOverwriteUserEditEvenIfTheyMoveItemsBack() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, c), 0, false)
        val deletion = QueueRemovalRecovery(player, setOf(a.mediaKey))
        deletion.remove()
        val removed = player.queue.stateSnapshot()
        player.editQueue { move(0, 1) }
        player.editQueue { move(1, 0) }
        assertEquals(removed.list, player.queue.stateSnapshot().list)
        assertEquals(removed.index, player.queue.stateSnapshot().index)
        assertFalse(deletion.restore(listOf(a)))
        assertEquals(listOf(b, c), player.queue.stateSnapshot().list)
    }

    @Test fun nativeMetadataAcknowledgementDoesNotPreventDeletionRecovery() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b), 0, false)
        val deletion = QueueRemovalRecovery(player, setOf(a.mediaKey))
        deletion.remove()
        player.queue.update(QueueUpdateReason.Sync) { replaceAll(listOf(b.copy(title = "refreshed")), 0) }
        val restoredAudio = a.copy(title = "restored", available = true)
        assertTrue(deletion.restore(listOf(restoredAudio)))
        assertEquals(restoredAudio, player.queue.currentItem())
        assertFalse(player.isPlaying.value)
    }

    @Test fun partialNativeRemovalFailureStillHasARecoveryReceipt() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b), 0, true)
        player.seekTo(9000)
        val deletion = QueueRemovalRecovery(player, setOf(a.mediaKey))
        player.nextLoadFailure = IllegalStateException("native load failed after queue publication")
        assertFailsWith<IllegalStateException> { deletion.remove() }
        assertEquals(listOf(b), player.queue.stateSnapshot().list)
        assertTrue(deletion.restore(listOf(a)))
        assertEquals(a, player.queue.currentItem())
        assertEquals(9000L, player.currentPosition())
        assertFalse(player.isPlaying.value)
    }

    @Test fun explicitSameSongSelectionTakesOwnershipFromDeletion() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b), 0, false)
        val deletion = QueueRemovalRecovery(player, setOf(a.mediaKey))
        deletion.remove()
        player.playAudio(b)
        assertFalse(deletion.restore(listOf(a)))
        assertEquals(b, player.queue.currentItem())
        assertTrue(player.isPlaying.value)
    }

    @Test fun explicitClearOfAlreadyEmptyQueuePreventsDeletionRecovery() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a), 0, false)
        val deletion = QueueRemovalRecovery(player, setOf(a.mediaKey))
        deletion.remove()
        assertTrue(player.queue.stateSnapshot().list.isEmpty())
        player.clearPlaylist()
        assertFalse(deletion.restore(listOf(a)))
        assertTrue(player.queue.stateSnapshot().list.isEmpty())
    }

    @Test fun recorderRejectsLatePositionWhenSameSongIsReloadedAtSameIndex() = runTest {
        val storage = MemoryHistory()
        val player = DevicePlayback(backgroundScope, storage)
        runCurrent()
        player.updatePlaylist(listOf(a), 0, false)
        val oldSample = CompletableDeferred<Long?>()
        val sampling = CompletableDeferred<Unit>()
        player.historyPositionReader = {
            sampling.complete(Unit)
            oldSample.await()
        }
        advanceTimeBy(1100)
        runCurrent()
        assertTrue(sampling.isCompleted)

        // Same song, same slot, same update reason, but a new load now owns the position.
        player.updatePlaylist(listOf(a), 0, false)
        player.seekTo(7000)
        oldSample.complete(42000)
        runCurrent()
        assertTrue(storage.snapshots.none { it.second == 42000L })

        player.historyPositionReader = { player.currentPosition() }
        advanceTimeBy(1100)
        runCurrent()
        assertEquals(7000L, storage.snapshots.last().second)
    }

    @Test fun recorderRejectsLatePositionFromPreviousSourceAndThenSavesNewSelection() = runTest {
        val storage = MemoryHistory()
        val player = DevicePlayback(backgroundScope, storage)
        runCurrent()
        player.updatePlaylist(listOf(a), 0, false)
        val oldSample = CompletableDeferred<Long?>()
        val sampling = CompletableDeferred<Unit>()
        player.historyPositionReader = {
            sampling.complete(Unit)
            oldSample.await()
        }
        advanceTimeBy(1100)
        runCurrent()
        assertTrue(sampling.isCompleted)

        // The raw ID is identical, but this is a different source and a different recording owner.
        val replacement = a.copy(mediaSourceName = "replacement-source")
        player.updatePlaylist(listOf(replacement), 0, false)
        player.seekTo(7000)
        runCurrent()
        oldSample.complete(42000)
        runCurrent()
        assertTrue(storage.snapshots.none { it.second == 42000L })

        player.historyPositionReader = { player.currentPosition() }
        advanceTimeBy(1100)
        runCurrent()
        assertEquals(7000L, storage.snapshots.last().second)
        assertEquals(listOf("replacement-source"), storage.snapshots.last().first.sourceNames)
        assertFalse(player.isPlaying.value)
    }

    @Test fun removingAndReaddingSongDoesNotRestoreItsPlayNextRequest() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, c), 0, false)
        player.setPlaybackMode(PlaybackMode.SEQUENTIAL)
        player.playNext(c)
        QueueAction.Remove(c).execute(player)
        QueueAction.AddToEnd(c).execute(player)
        player.skipToNext()
        assertEquals(b, player.queue.currentItem())
    }

    @Test fun replacingQueueWithSameSongsDiscardsPreviousPlayNextRequest() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, c), 0, false)
        player.setPlaybackMode(PlaybackMode.SEQUENTIAL)
        player.playNext(c)
        player.updatePlaylist(listOf(a, b, c), 0, false)
        player.skipToNext()
        assertEquals(b, player.queue.currentItem())
    }

    @Test fun sequentialCompletionStopsAtTheLastSong() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b), 1, true)
        player.setPlaybackMode(PlaybackMode.SEQUENTIAL)
        player.completeSong()
        assertEquals(b, player.queue.currentItem())
        assertFalse(player.isPlaying.value)
    }

    @Test fun loopCompletionReturnsToTheFirstSong() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b), 1, true)
        player.setPlaybackMode(PlaybackMode.LOOP)
        player.completeSong()
        assertEquals(a, player.queue.currentItem())
        assertTrue(player.isPlaying.value)
    }

    @Test fun explicitNextStillPlaysAfterTheSequentialLastSong() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, c), 2, true)
        player.setPlaybackMode(PlaybackMode.SEQUENTIAL)
        player.playNext(a)
        player.completeSong()
        assertEquals(a, player.queue.currentItem())
        assertTrue(player.isPlaying.value)
        player.skipTo(2, true)
        player.completeSong()
        assertEquals(c, player.queue.currentItem())
        assertFalse(player.isPlaying.value)
    }

    @Test fun explicitPlayNextOverridesShuffleWithoutDuplicatingSong() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, c), 0, false)
        player.setPlaybackMode(PlaybackMode.SHUFFLE)
        player.playNext(c)
        player.skipToNext()
        assertEquals(c, player.queue.currentItem())
        assertEquals(3, player.queue.stateSnapshot().list.size)
    }

    @Test fun explicitPlayNextOverridesSingleRepeat() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b), 0, false)
        player.setPlaybackMode(PlaybackMode.SINGLE_LOOP)
        player.playNext(b)
        player.skipToNext()
        assertEquals(b, player.queue.currentItem())
    }

    @Test fun requestedShuffleSongAppearsBeforeThePreviouslyPlayingSong() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, c), 1, false)
        player.setPlaybackMode(PlaybackMode.SHUFFLE)
        player.playNext(a)
        player.skipToNext()
        assertEquals(listOf(a, b), player.queue.stateSnapshot().rearrange().take(2))
        assertEquals(setOf(a, b, c), player.queue.stateSnapshot().list.toSet())
    }

    @Test fun requestedShuffleSongWrapsBeforeTheFirstSlotWithoutDuplicatingIt() = runTest {
        val player = DevicePlayback(backgroundScope)
        runCurrent()
        player.updatePlaylist(listOf(a, b, c), 0, false)
        player.setPlaybackMode(PlaybackMode.SHUFFLE)
        player.playNext(b)
        player.completeSong()
        assertEquals(listOf(b, a), player.queue.stateSnapshot().rearrange().take(2))
        assertEquals(3, player.queue.stateSnapshot().list.size)
    }

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
        suspend fun completeSong() = onCompletion()
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
        val snapshots = mutableListOf<Pair<HistoryQueueIdentity, Long>>()
        override fun saveSnapshot(identity: HistoryQueueIdentity, position: Long) {
            snapshots += identity to position
            savePosition(position)
        }
        override fun savedPosition() = position
        override fun savePosition(position: Long) { this.position = position }
    }
}
