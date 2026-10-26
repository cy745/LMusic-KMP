package com.lalilu.lplayer.core

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lplayer.playback.*
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CorePlaybackTest {
    @Test fun pausedNavigationWrapsAndSameSelectionPreservesPosition() = runTest {
        val f = fixture()
        f.playback.updatePlaylist(f.songs, 0, false)
        runCurrent()
        f.playback.seekTo(400)
        runCurrent()
        f.playback.playAudio(f.songs[0])
        assertEquals(400, f.playback.currentPosition())
        assertFalse(f.playback.isPlaying.value)
        f.playback.skipToPrevious()
        runCurrent()
        assertEquals(f.songs.last(), f.playback.queue.currentItem())
        assertEquals(PlayIntent.Pause, f.playback.core.state.value.intent)
        f.playback.skipToNext()
        runCurrent()
        assertEquals(f.songs.first(), f.playback.queue.currentItem())
    }

    @Test fun randomPendingIsSingleAndMetadataRefreshDoesNotRevokeIt() = runTest {
        val f = fixture()
        f.playback.updatePlaylist(f.songs, 0, false); runCurrent()
        f.playback.setPlaybackMode(PlaybackMode.SHUFFLE)
        f.playback.playNext(f.songs[1]); f.playback.playNext(f.songs[2])
        assertEquals(f.songs[2], f.playback.pendingNext.value)
        assertEquals(f.songs, f.playback.queue.stateSnapshot().list)
        f.playback.queue.update(QueueUpdateReason.Sync) { replace(0, f.songs[0].copy(title = "refreshed")) }
        assertEquals(f.songs[2], f.playback.pendingNext.value)
        f.playback.skipToNext(); runCurrent()
        assertEquals(f.songs[2], f.playback.queue.currentItem())
        // Paused preparation has not consumed the intent until actual playback starts.
        f.playback.play(); runCurrent(); advanceTimeBy(101); runCurrent()
        assertNull(f.playback.pendingNext.value)
    }

    @Test fun deleteLastCurrentWrapsAndDeduplicatesSourceQualifiedSongs() = runTest {
        val f = fixture()
        f.playback.updatePlaylist(f.songs + f.songs[0], 2, false); runCurrent()
        assertEquals(3, f.playback.queue.stateSnapshot().list.size)
        f.playback.editQueue { remove(f.songs[2]) }; runCurrent()
        assertEquals(f.songs[0], f.playback.queue.currentItem())
        assertFalse(f.playback.isPlaying.value)
        f.playback.editQueue { addToEnd(listOf(f.songs[0].copy(mediaSourceName = "other"))) }
        assertEquals(3, f.playback.queue.stateSnapshot().list.size)
    }

    @Test fun lateResolutionCannotReplaceLatestSelectionAndPauseKeepsPreparation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val f = fixture { audio -> if (audio.id == "A") gate.await() }
        f.playback.updatePlaylist(f.songs, 0, true); runCurrent()
        f.playback.pause()
        f.playback.skipToNext(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("B", f.engine.loaded)
        assertEquals(Phase.ReadyPaused, f.playback.core.state.value.phase)
        assertFalse(f.engine.state.value.isPlaying)
    }

    @Test fun failureWhilePausedStaysAndRetryHasFreshFailureRound() = runTest {
        var bad = true
        val attempted = mutableListOf<String>()
        val f = fixture { audio -> attempted += audio.id; if (bad) error("missing") }
        f.playback.updatePlaylist(f.songs, 0, false); runCurrent()
        assertEquals(listOf("A"), attempted)
        assertEquals(Phase.Failed, f.playback.core.state.value.phase)
        f.playback.play(); runCurrent()
        assertEquals(listOf("A", "A", "B", "C"), attempted)
        assertEquals(PlayIntent.Pause, f.playback.core.state.value.intent)
        bad = false
        f.playback.play(); runCurrent(); advanceTimeBy(101); runCurrent()
        assertEquals(Phase.Playing, f.playback.core.state.value.phase)
    }

    @Test fun naturalSequentialEndStopsButManualNextWrapsAndRepeatOneKeepsCurrent() = runTest {
        val f = fixture()
        f.playback.setPlaybackMode(PlaybackMode.SEQUENTIAL)
        f.playback.updatePlaylist(f.songs, 2, true); runCurrent(); advanceTimeBy(101); runCurrent()
        f.engine.onEvent?.invoke(PlaybackEngineEvent.Completion); runCurrent()
        assertEquals(Phase.Ended, f.playback.core.state.value.phase)
        f.playback.skipToNext(); runCurrent()
        assertEquals("A", f.playback.queue.currentItem()?.id)
        f.playback.setPlaybackMode(PlaybackMode.SINGLE_LOOP)
        f.playback.play(); runCurrent()
        f.engine.onEvent?.invoke(PlaybackEngineEvent.Completion); runCurrent()
        assertEquals("A", f.playback.queue.currentItem()?.id)
    }

    @Test fun preparingDecoderFailureAndTimeoutReachCoreWithoutHanging() = runTest {
        val f = fixture()
        f.engine.delayedPreparation = true
        f.playback.updatePlaylist(f.songs, 0, false); runCurrent()
        f.playback.pause()
        advanceTimeBy(30_001); runCurrent()
        assertEquals(Phase.Failed, f.playback.core.state.value.phase)
        assertEquals("A", f.playback.queue.currentItem()?.id)
    }

    @Test fun manualPauseDuringSystemInterruptionPreventsResume() = runTest {
        val f = fixture()
        f.playback.updatePlaylist(f.songs, 0, true); runCurrent(); advanceTimeBy(101); runCurrent()
        val token = assertNotNull(f.playback.pauseForInterruption())
        f.playback.pause()
        f.playback.resumeAfterInterruption(token)
        assertEquals(PlayIntent.Pause, f.playback.core.state.value.intent)
        f.playback.play(); runCurrent()
        val eligible = assertNotNull(f.playback.pauseForInterruption())
        f.playback.resumeAfterInterruption(eligible)
        assertEquals(PlayIntent.Play, f.playback.core.state.value.intent)
    }

    @Test fun replacingQueueWithoutCurrentChoosesFirstInsteadOfDeletionSuccessor() = runTest {
        val f = fixture()
        f.playback.updatePlaylist(f.songs, 0, false); runCurrent()
        f.playback.editQueue { replaceAll(listOf(f.songs[2], f.songs[1])) }; runCurrent()
        assertEquals("C", f.playback.queue.currentItem()?.id)
        assertEquals(PlayIntent.Pause, f.playback.core.state.value.intent)
    }

    private fun TestScope.fixture(read: suspend (LAudio) -> Unit = {}): Fixture {
        val songs = listOf("A", "B", "C").map { LAudio(id = it, mediaSourceName = "fixture") }
        val repository = object : AudioRepository {
            override fun getAudios() = flowOf(songs)
            override fun getAudios(ids: List<String>) = flowOf(songs.filter { it.id in ids })
            override fun getAudio(id: String) = flowOf(songs.find { it.id == id })
            override suspend fun clearUnavailableAudio() {}
        }
        val source = object : MediaSource {
            override val name = "fixture"
            override val state = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
            override val snapshot = MutableStateFlow<Snapshot?>(null)
            override val contentState = MutableStateFlow(MediaContentState(MediaContentAvailability.Ready))
            override val dataSource = object : MediaDataSource {
                override suspend fun getMedia(song: LAudio): MediaData { read(song); return MediaData.Url(song.id) }
            }
        }
        val engine = Engine()
        val playback = CorePlayback(backgroundScope, EngineAudioRuntime(backgroundScope, listOf(engine)),
            PlatformMediaSource.provide(source), repository, PlaybackHistoryImpl(object : HistoryStorage {
                override fun savedPosition() = 0L
                override fun savePosition(position: Long) {}
            }))
        return Fixture(playback, engine, songs)
    }
    private data class Fixture(val playback: CorePlayback, val engine: Engine, val songs: List<LAudio>)
    private class Engine : PlaybackEngine {
        override val state = MutableStateFlow(PlaybackEngineState.EMPTY)
        override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null
        var loaded: String? = null
        var delayedPreparation = false
        override fun canHandle(mediaData: MediaData, audio: LAudio) = true
        override suspend fun load(mediaData: MediaData, audio: LAudio) {
            loaded = audio.id
            state.value = PlaybackEngineState(duration = 1000, isLoading = delayedPreparation)
        }
        override suspend fun play() { state.value = state.value.copy(isPlaying = true) }
        override suspend fun pause() { state.value = state.value.copy(isPlaying = false) }
        override suspend fun stop() { pause() }
        override suspend fun seekTo(positionMs: Long) { state.value = state.value.copy(position = positionMs) }
        override fun currentPosition() = state.value.position
        override suspend fun release() { state.value = PlaybackEngineState.EMPTY }
    }
}
