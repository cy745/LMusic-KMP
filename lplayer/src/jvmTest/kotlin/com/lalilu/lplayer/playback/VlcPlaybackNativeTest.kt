package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.repository.PlaybackFailureRepository
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lplayer.player.VLCPlayerLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assume.assumeTrue
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import kotlin.math.abs
import kotlin.test.*

/** Real VLCPlayback + common queue/history + native VLC; repository/content are controlled fixtures. */
class VlcPlaybackNativeTest {
    @Test fun playAfterStopSkipsCurrentSongIfItsFileBecameUnreadable() = runBlocking {
        var failFirst = false
        val attempted = mutableListOf<String>()
        withPlayback(beforeRead = { song ->
            attempted += song.id
            if (failFirst && song.id == "first") throw java.io.FileNotFoundException("test")
        }) { playback, native, _, song ->
            val first = song.copy(id = "first")
            val next = song.copy(id = "next")
            playback.setPlaybackMode(PlaybackMode.SEQUENTIAL)
            playback.updatePlaylist(listOf(first, next), 0, false)
            playback.stop()
            failFirst = true
            attempted.clear()
            playback.play()
            assertEquals(listOf("first", "next"), attempted)
            assertEquals(next, playback.queue.currentItem())
            withTimeout(5000) { while (!native.status().isPlaying) delay(25) }
            playback.pause()
        }
    }

    @Test fun failedPreviousContinuesBackwardToTheNextReadableSong() = runBlocking {
        val attempted = mutableListOf<String>()
        val failures = MemoryFailures()
        withPlayback(failureRepository = failures, beforeRead = { song ->
            attempted += song.id
            if (song.id == "bad") throw java.io.FileNotFoundException("test")
        }) { playback, native, _, song ->
            val first = song.copy(id = "first")
            val bad = song.copy(id = "bad")
            val last = song.copy(id = "last")
            playback.updatePlaylist(listOf(first, bad, last), 2, false)
            playback.setPlaybackMode(PlaybackMode.SEQUENTIAL)
            attempted.clear()
            playback.skipToPrevious()
            assertEquals(listOf("bad", "first"), attempted)
            assertEquals(first, playback.queue.currentItem())
            withTimeout(5000) { while (!native.status().isPlaying) delay(25) }
            assertEquals(PlaybackFailureReason.FileMissing, failures.failures.value[bad.playbackId]?.reason)
            playback.pause()
        }
    }

    @Test fun allMissingLoopSongsAreTriedOnlyOnceAndStop() = runBlocking {
        val attempted = mutableListOf<String>()
        withPlayback(beforeRead = { song ->
            attempted += song.id
            throw java.io.FileNotFoundException("test")
        }) { playback, native, _, song ->
            val songs = (0..2).map { song.copy(id = "bad-$it") }
            playback.setPlaybackMode(PlaybackMode.LOOP)
            assertFailsWith<java.io.FileNotFoundException> { playback.updatePlaylist(songs, 0, true) }
            assertEquals(listOf("bad-0", "bad-1", "bad-2"), attempted)
            assertFalse(native.status().isPlaying)
            assertFalse(playback.isPlaying.value)
        }
    }

    @Test fun pausedPreparationKeepsFailureUntilRealPlaybackAdvances() = runBlocking {
        val failures = MemoryFailures()
        withPlayback(failureRepository = failures) { playback, native, _, song ->
            val other = song.copy(mediaSourceName = "other-source")
            val remembered = PlaybackFailure(PlaybackFailureReason.FileMissing, 1L)
            failures.record(song.playbackId, remembered)
            failures.record(other.playbackId, remembered)
            playback.updatePlaylist(listOf(song), 0, false)
            delay(750)
            assertEquals(remembered, failures.failures.value[song.playbackId])
            assertFalse(native.status().isPlaying)
            playback.play()
            withTimeout(5_000) {
                while (song.playbackId in failures.failures.value) delay(25)
            }
            assertTrue(native.status().time() > 0)
            assertEquals(mapOf(other.playbackId to remembered), failures.failures.value)
            playback.pause()
        }
    }

    @Test fun failedLoadPersistsOnlyTheCommandOwnedSourceQualifiedSong() = runBlocking {
        val failures = MemoryFailures()
        val missing = java.io.FileNotFoundException("private-test-path")
        withPlayback(beforeRead = { throw missing }, failureRepository = failures) { playback, _, _, song ->
            val caught = assertFailsWith<java.io.FileNotFoundException> {
                playback.updatePlaylist(listOf(song), 0, true)
            }
            // Coroutine stack-trace recovery may copy the exception and retain it as the cause.
            assertTrue(generateSequence<Throwable>(caught) { it.cause }.any { it === missing })
            assertEquals(setOf(song.playbackId), failures.failures.value.keys)
            assertEquals(PlaybackFailureReason.FileMissing, failures.failures.value.getValue(song.playbackId).reason)
        }
    }

    @Test fun storageFailureDoesNotReplaceTheOriginalLoadFailure() = runBlocking {
        val failures = MemoryFailures(failWrites = true)
        val missing = java.io.FileNotFoundException("private-test-path")
        withPlayback(beforeRead = { throw missing }, failureRepository = failures) { playback, _, _, song ->
            val caught = assertFailsWith<java.io.FileNotFoundException> {
                playback.updatePlaylist(listOf(song), 0, true)
            }
            assertTrue(generateSequence<Throwable>(caught) { it.cause }.any { it === missing })
        }
    }

    @Test fun pauseCancelsTheAdaptersPendingSelectionWithoutApplyingItsLateResult() = runBlocking {
        val failures = MemoryFailures()
        val resolving = CompletableDeferred<Unit>()
        val resolved = CompletableDeferred<Unit>()
        withPlayback(failureRepository = failures, beforeRead = { song ->
            if (song.id == "delayed") {
                resolving.complete(Unit)
                resolved.await()
            }
        }) { playback, native, _, song ->
            val delayed = song.copy(id = "delayed")
            playback.updatePlaylist(listOf(song, delayed), 0, false)
            coroutineScope {
                val selection = launch { playback.skipTo(1, true) }
                withTimeout(5_000) { resolving.await() }
                assertNull(playback.sampleHistoryPosition(), "Pending native selection must not yield a historical position")
                playback.pause()
                resolved.complete(Unit)
                withTimeout(5_000) { selection.join() }
                assertTrue(selection.isCancelled)
                assertTrue(failures.failures.value.isEmpty(), "Cancellation is not a song failure")
                assertEquals(song, playback.queue.currentItem())
                assertFalse(native.status().isPlaying)
                delay(200)
                assertFalse(native.status().isPlaying)
                playback.play()
                withTimeout(5_000) {
                    while (!native.status().isPlaying || native.status().time() < 200) delay(25)
                }
                assertEquals(song, playback.queue.currentItem())
                playback.pause()
            }
        }
    }

    @Test fun stoppingClearsPositionInsteadOfExtrapolatingTheLastPlayingEvent() = runBlocking {
        withPlayback { playback, native, storage, song ->
            playback.updatePlaylist(listOf(song), 0, true)
            withTimeout(5_000) { while (native.status().time() < 1_000) delay(25) }
            playback.stop()
            delay(1_500)
            assertFalse(native.status().isPlaying)
            assertEquals(0L, playback.currentPosition())
            assertEquals(0L, storage.savedPosition())
            assertFalse(playback.isPlaying.value)
        }
    }

    @Test fun pausedSeekAndHistoryRecordingStayAtTheNativePosition() = runBlocking {
        withPlayback { playback, native, storage, song ->
            playback.updatePlaylist(listOf(song), 0, false)
            playback.seekTo(12_000)
            withTimeout(5_000) { while (abs(native.status().time() - 12_000) > 500) delay(25) }
            assertEquals(State.PAUSED, native.status().state())
            val position = native.status().time()
            assertTrue(abs(playback.sampleHistoryPosition()!! - position) < 100)
            delay(1_500)
            assertTrue(abs(playback.currentPosition() - position) < 100, "Paused public position drifted")
            assertTrue(abs(storage.savedPosition() - position) < 100, "Paused history position drifted")
            assertFalse(playback.isPlaying.value)
        }
    }

    @Test fun queueReplacementCannotSampleTheOldLoadedSong() = runBlocking {
        withPlayback { playback, _, _, song ->
            playback.updatePlaylist(listOf(song), 0, false)
            playback.seekTo(12_000)
            val oldQueue = playback.queue.stateSnapshot()
            val replacement = song.copy(id = "not-loaded")
            playback.queue.update { replaceAll(listOf(replacement), 0) }
            assertNull(playback.historyPosition(oldQueue))
            assertNull(playback.sampleHistoryPosition())
            playback.queue.update { replaceAll(listOf(song.copy(mediaSourceName = "other-source")), 0) }
            assertNull(playback.sampleHistoryPosition(), "Same ID in a different source is not the loaded song")
        }
    }

    @Test fun historyHookLoadsNativeAudioAndPreservesTheRestoredQueue() = runBlocking {
        withPlayback { playback, native, _, song ->
            val other = song.copy(id = "other")
            playback.queue.update { replaceAll(listOf(other, song), 1) }
            playback.onQueueRestored(PlaybackHistory.HistorySnapshot(
                listOf(other.id, song.id), 1, 12_000, listOf(song.mediaSourceName, song.mediaSourceName),
            ))
            assertEquals(State.PAUSED, native.status().state())
            assertTrue(abs(native.status().time() - 12_000) <= 500)
            assertEquals(listOf(other, song), playback.queue.stateSnapshot().list)
            assertEquals(1, playback.queue.stateSnapshot().index)
            playback.play()
            withTimeout(5_000) {
                while (!native.status().isPlaying || native.status().time() <= 12_000) delay(25)
            }
            playback.pause()
            withTimeout(5_000) { while (native.status().state() != State.PAUSED) delay(25) }
            assertFalse(playback.isPlaying.value)
        }
    }

    private suspend fun withPlayback(
        beforeRead: suspend (LAudio) -> Unit = {},
        failureRepository: PlaybackFailureRepository = MemoryFailures(),
        block: suspend (VLCPlayback, MediaPlayer, MemoryHistory, LAudio) -> Unit,
    ) {
        val resources = System.getenv("LMUSIC_NATIVE_RESOURCES")
        val fixture = System.getenv("LMUSIC_NATIVE_AUDIO_FIXTURE")
        assumeTrue("Native VLC resources and audio fixture not configured", resources != null && fixture != null)
        System.setProperty("compose.application.resources.dir", resources!!)
        VLCPlayerLoader.initialize().join()
        val factory = MediaPlayerFactory(null as NativeDiscovery?, "--no-video", "--aout=dummy")
        val native = factory.mediaPlayers().newMediaPlayer()
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val source = object : MediaSource {
            override val name = "native-fixture"
            override val state = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
            override val snapshot = MutableStateFlow<Snapshot?>(null)
            override val contentState = MutableStateFlow(MediaContentState(MediaContentAvailability.Ready))
            override val dataSource = object : MediaDataSource {
                override suspend fun getMedia(song: LAudio): MediaData {
                    beforeRead(song)
                    return MediaData.Url(fixture!!)
                }
            }
        }
        val song = LAudio(id = "native-audio", mediaSourceName = source.name)
        val repository = object : AudioRepository {
            override fun getAudios() = flowOf(listOf(song))
            override fun getAudios(ids: List<String>) = flowOf(listOf(song).filter { it.id in ids })
            override fun getAudio(id: String) = flowOf(song.takeIf { it.id == id })
            override suspend fun clearUnavailableAudio() = Unit
        }
        val tracker = object : IPlaybackDataTracker {
            override fun onMediaItemTransition(mediaId: String?, title: String?, isRepeating: Boolean, isNormalTransition: Boolean) = Unit
            override fun onIsPlayingChanged(isPlaying: Boolean) = Unit
        }
        val storage = MemoryHistory()
        val ready = CompletableDeferred<Unit>()
        try {
            val playback = VLCPlayback(repository, PlaybackHistoryImpl(storage), PlatformMediaSource.provide(source),
                tracker, failureRepository, scope, nativePlayerProvider = { native }, installPlatformControls = { ready.complete(Unit) })
            withTimeout(5_000) { ready.await() }
            block(playback, native, storage, song)
        } finally {
            job.cancelAndJoin()
            native.controls().stop()
            native.release()
            factory.release()
        }
    }

    private class MemoryHistory : HistoryStorage {
        @Volatile private var position = 0L
        override fun savedPlaylistIds() = emptyList<String>()
        override fun savedPlayId() = ""
        override fun savedPosition() = position
        override fun savePlaylistIds(ids: List<String>) = Unit
        override fun savePlayId(id: String) = Unit
        override fun savePosition(position: Long) { this.position = position }
    }

    private class MemoryFailures(private val failWrites: Boolean = false) : PlaybackFailureRepository {
        override val failures = MutableStateFlow<Map<String, PlaybackFailure>>(emptyMap())
        override suspend fun record(playbackId: String, failure: PlaybackFailure) {
            check(!failWrites) { "test database unavailable" }
            failures.value = failures.value + (playbackId to failure)
        }
        override suspend fun clearAfterSuccessfulPlayback(playbackId: String) {
            failures.value = failures.value - playbackId
        }
    }
}
