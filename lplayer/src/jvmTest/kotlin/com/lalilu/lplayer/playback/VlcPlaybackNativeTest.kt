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
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.State
import java.io.File
import kotlin.math.abs
import kotlin.test.*

/** Real VLCPlayback + common queue/history + native VLC; repository/content are controlled fixtures. */
class VlcPlaybackNativeTest {
    @Test fun pausedNavigationAndSameSelectionMatchSharedCore() = runBlocking {
        withPlayback { playback, native, _, song ->
            val songs = listOf(song.copy(id = "first"), song.copy(id = "last"))
            playback.updatePlaylist(songs, 0, false)
            awaitPhase(playback, com.lalilu.playback.core.Phase.ReadyPaused)
            playback.seekTo(3000)
            withTimeout(5000) { playback.core.state.first { it.pendingSeekId == null && it.positionMs >= 2500 } }
            playback.playAudio(songs[0])
            assertFalse(native.status().isPlaying)
            assertTrue(playback.currentPosition() >= 2500)
            playback.skipToPrevious()
            awaitPhase(playback, com.lalilu.playback.core.Phase.ReadyPaused)
            assertEquals("last", playback.queue.currentItem()?.id)
            assertFalse(native.status().isPlaying)
            playback.skipToNext()
            awaitPhase(playback, com.lalilu.playback.core.Phase.ReadyPaused)
            assertEquals("first", playback.queue.currentItem()?.id)
        }
    }

    @Test fun slowSelectionCanBeReplacedAndPauseDoesNotResumeOnPrepare() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        withPlayback(beforeRead = { if (it.id == "slow") { entered.complete(Unit); gate.await() } }) { playback, native, _, song ->
            playback.updatePlaylist(listOf(song.copy(id = "slow"), song.copy(id = "good")), 0, true)
            withTimeout(5000) { entered.await() }
            playback.pause()
            playback.skipToNext()
            gate.complete(Unit)
            awaitPhase(playback, com.lalilu.playback.core.Phase.ReadyPaused)
            assertEquals("good", playback.queue.currentItem()?.id)
            assertFalse(native.status().isPlaying)
        }
    }

    @Test fun allFailuresAreBoundedAndPlayRetriesSelected() = runBlocking {
        val attempts = java.util.concurrent.CopyOnWriteArrayList<String>()
        var bad = true
        withPlayback(beforeRead = { attempts += it.id; if (bad) throw java.io.FileNotFoundException("fixture") }) { playback, native, _, song ->
            val songs = (0..2).map { song.copy(id = "bad-$it") }
            playback.updatePlaylist(songs, 0, true)
            awaitPhase(playback, com.lalilu.playback.core.Phase.Failed)
            withTimeout(5000) { playback.core.state.first { it.intent == com.lalilu.playback.core.PlayIntent.Pause } }
            assertEquals(listOf("bad-0", "bad-1", "bad-2"), attempts.toList())
            val selected = playback.queue.currentItem()?.id
            bad = false
            playback.play()
            awaitPhase(playback, com.lalilu.playback.core.Phase.Playing)
            assertEquals(selected, attempts.last())
            assertTrue(native.status().isPlaying)
        }
    }

    @Test fun nativeDecodeFailureSkipsAndRepeatOneFailureStops() = runBlocking {
        withPlayback(mediaUrl = { if (it.id == "missing") "/private/tmp/lmusic-p4-nonexistent.wav" else null }) { playback, _, _, song ->
            playback.updatePlaylist(listOf(song.copy(id = "missing"), song.copy(id = "good")), 0, true)
            awaitPhase(playback, com.lalilu.playback.core.Phase.Playing)
            assertEquals("good", playback.queue.currentItem()?.id)
            playback.setPlaybackMode(PlaybackMode.SINGLE_LOOP)
            playback.playAudio(song.copy(id = "missing"))
            awaitPhase(playback, com.lalilu.playback.core.Phase.Failed)
            assertEquals("missing", playback.queue.currentItem()?.id)
            assertEquals(com.lalilu.playback.core.PlayIntent.Pause, playback.core.state.value.intent)
        }
    }

    @Test fun nativePositionAndHistoryStayPausedAfterSeek() = runBlocking {
        withPlayback { playback, native, history, song ->
            playback.updatePlaylist(listOf(song), 0, true)
            awaitPhase(playback, com.lalilu.playback.core.Phase.Playing)
            playback.pause()
            playback.seekTo(5000)
            withTimeout(5000) { playback.core.state.first { it.pendingSeekId == null && it.positionMs >= 4500 } }
            awaitPhase(playback, com.lalilu.playback.core.Phase.ReadyPaused)
            delay(1300)
            assertFalse(native.status().isPlaying)
            assertTrue(abs(history.savedPosition() - native.status().time()) < 500)
            val before = playback.currentPosition()
            delay(400)
            assertTrue(abs(before - playback.currentPosition()) < 100)
        }
    }

    private suspend fun awaitPhase(playback: VLCPlayback, phase: com.lalilu.playback.core.Phase) {
        withTimeout(12_000) { playback.core.state.first { it.phase == phase } }
    }

    private val slowUrl = "http://10.255.255.1/lmusic-slow-probe.mp3"

    private suspend fun withPlayback(
        beforeRead: suspend (LAudio) -> Unit = {},
        failureRepository: PlaybackFailureRepository = MemoryFailures(),
        /** 默认所有歌曲都用同一个真实音频夹具；按 id 返回不同 URL 可模拟慢加载/坏内容。 */
        mediaUrl: (LAudio) -> String? = { null },
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
        val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + job)
        val source = object : MediaSource {
            override val name = "native-fixture"
            override val state = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
            override val snapshot = MutableStateFlow<Snapshot?>(null)
            override val contentState = MutableStateFlow(MediaContentState(MediaContentAvailability.Ready))
            override val dataSource = object : MediaDataSource {
                override suspend fun getMedia(song: LAudio): MediaData {
                    beforeRead(song)
                    return MediaData.Url(mediaUrl(song) ?: fixture!!)
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
        var host: VLCPlayback? = null
        try {
            val playback = VLCPlayback(repository, PlaybackHistoryImpl(storage), PlatformMediaSource.provide(source),
                tracker, failureRepository, scope, nativePlayerProvider = { native }, installPlatformControls = { ready.complete(Unit) })
            host = playback
            withTimeout(5_000) { ready.await() }
            block(playback, native, storage, song)
        } finally {
            withContext(scope.coroutineContext.minusKey(Job)) { host?.core?.close(); delay(100) }
            job.cancelAndJoin()
            native.controls().stop()
            native.release()
            factory.release()
        }
    }

    private class MemoryHistory : HistoryStorage {
        @Volatile private var position = 0L
        override fun savedPosition() = position
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
