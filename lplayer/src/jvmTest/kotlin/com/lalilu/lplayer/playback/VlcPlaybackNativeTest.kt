package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
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
    @Test fun pauseCancelsTheAdaptersPendingSelectionWithoutApplyingItsLateResult() = runBlocking {
        val resolving = CompletableDeferred<Unit>()
        val resolved = CompletableDeferred<Unit>()
        withPlayback(beforeRead = { song ->
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
                tracker, scope, nativePlayerProvider = { native }, installPlatformControls = { ready.complete(Unit) })
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
}
