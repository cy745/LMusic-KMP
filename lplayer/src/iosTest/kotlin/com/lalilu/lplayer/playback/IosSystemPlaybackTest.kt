package com.lalilu.lplayer.playback

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.core.*
import com.lalilu.lplayer.helper.AudioSessionHelper
import com.lalilu.lplayer.notifacation.NowPlayingInfoNotification
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import platform.AVFAudio.*
import platform.Foundation.*
import platform.MediaPlayer.*
import kotlin.test.*
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalForeignApi::class)
class IosSystemPlaybackTest {
    @Test fun interruptionResumeRespectsUserIntentAndRebinding() = onMain { scope, host, engine, songs ->
        LPlayerKV.handleAudioFocus.value = true
        AudioSessionHelper.bindPlayback(host)
        AudioSessionHelper.bindPlayback(host)
        host.play(); until { host.isPlaying.value }
        interrupt(AVAudioSessionInterruptionTypeBegan)
        until { !host.isPlaying.value }
        interrupt(AVAudioSessionInterruptionTypeEnded, true)
        until { host.isPlaying.value }
        interrupt(AVAudioSessionInterruptionTypeBegan)
        until { !host.isPlaying.value }
        host.pause()
        interrupt(AVAudioSessionInterruptionTypeEnded, true)
        delay(300)
        assertFalse(host.isPlaying.value)
        host.play(); until { host.isPlaying.value }
        interrupt(AVAudioSessionInterruptionTypeBegan)
        until { !host.isPlaying.value }
        host.skipToNext()
        interrupt(AVAudioSessionInterruptionTypeEnded, true)
        delay(300)
        assertFalse(host.isPlaying.value)
        assertEquals(songs[1].id, host.queue.currentItem()?.id)
    }

    @Test fun routeRemovalSwitchAndUnbindAreHonored() = onMain { _, host, _, _ ->
        AudioSessionHelper.bindPlayback(host)
        host.play(); until { host.isPlaying.value }
        LPlayerKV.handleBecomeNoisy.value = false
        routeLost(); delay(200)
        assertTrue(host.isPlaying.value)
        LPlayerKV.handleBecomeNoisy.value = true
        routeLost(); until { !host.isPlaying.value }
        AudioSessionHelper.unbindPlayback()
        host.play(); until { host.isPlaying.value }
        routeLost(); delay(200)
        assertTrue(host.isPlaying.value)
    }

    @Test fun systemMetadataFollowsPausedSeekAndRejectsOldArtwork() = onMain { _, host, _, songs ->
        val oldArtwork = CompletableDeferred<Unit>()
        val requested = mutableListOf<String>()
        NowPlayingInfoNotification.bindPlayback(host, artworkLoader = { request ->
            requested += request.audio.id
            if (request.audio.id == songs[0].id) withContext(NonCancellable) { oldArtwork.await() }
            null
        })
        val center = MPNowPlayingInfoCenter.defaultCenter()
        try {
            until { requested.isNotEmpty() }
            host.seekTo(2200)
            until { ((center.nowPlayingInfo?.get(MPNowPlayingInfoPropertyElapsedPlaybackTime) as? Number)?.toDouble() ?: -1.0) == 2.2 }
            host.playAudio(songs[1])
            until { center.nowPlayingInfo?.get(MPMediaItemPropertyTitle) == "B" }
            oldArtwork.complete(Unit)
            until { requested.lastOrNull() == songs[1].id }
            assertEquals("B", center.nowPlayingInfo?.get(MPMediaItemPropertyTitle))
            assertNull(center.nowPlayingInfo?.get(MPMediaItemPropertyArtwork))
            NowPlayingInfoNotification.unbind()
            host.seekTo(4000); delay(600)
            assertNull(center.nowPlayingInfo)
        } finally { oldArtwork.complete(Unit) }
    }

    private fun interrupt(type: ULong, resume: Boolean = false) {
        NSNotificationCenter.defaultCenter().postNotificationName(AVAudioSessionInterruptionNotification,
            AVAudioSession.sharedInstance(), mapOf(AVAudioSessionInterruptionTypeKey to NSNumber(unsignedLong = type),
                AVAudioSessionInterruptionOptionKey to NSNumber(unsignedLong = if (resume) AVAudioSessionInterruptionOptionShouldResume else 0uL)))
    }
    private fun routeLost() {
        NSNotificationCenter.defaultCenter().postNotificationName(AVAudioSessionRouteChangeNotification,
            AVAudioSession.sharedInstance(), mapOf(AVAudioSessionRouteChangeReasonKey to NSNumber(unsignedLong = AVAudioSessionRouteChangeReasonOldDeviceUnavailable)))
    }
    private suspend fun until(check: () -> Boolean) = withTimeout(5000) { while (!check()) delay(20) }

    private fun onMain(test: suspend (CoroutineScope, CorePlayback, FakeEngine, List<LAudio>) -> Unit) {
        KVContext.registerSaver(InMemoryKVSaver())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val songs = listOf(LAudio("a", "A", mediaSourceName = "system"), LAudio("b", "B", mediaSourceName = "system"))
        val repository = object : AudioRepository {
            override fun getAudios() = flowOf(songs)
            override fun getAudios(ids: List<String>) = flowOf(songs.filter { it.id in ids })
            override fun getAudio(id: String) = flowOf(songs.firstOrNull { it.id == id })
            override suspend fun clearUnavailableAudio() {}
        }
        val store = MediaSourceStateStore().apply { content.ready() }
        val source = object : MediaSource {
            override val name = "system"
            override val state = store.state
            override val snapshot = store.snapshot
            override val contentState = store.contentState
            override val dataSource = object : MediaDataSource {
                override suspend fun getMedia(song: LAudio) = MediaData.Url(song.id)
            }
        }
        val engine = FakeEngine().apply { stubDuration = 10000L }
        val runtimeEngine = object : PlaybackEngine by engine {
            override fun currentPosition() = engine.state.value.position
        }
        val host = CorePlayback(scope, EngineAudioRuntime(scope, listOf(runtimeEngine)), PlatformMediaSource.provide(source),
            repository, PlaybackHistoryImpl(object : HistoryStorage {
                override fun savedPosition() = 0L
                override fun savePosition(position: Long) {}
            }))
        val work = scope.async {
            host.updatePlaylist(songs, 0, false)
            until { !host.isLoading.value && host.queue.currentItem() != null }
            test(scope, host, engine, songs)
        }
        try {
            val mark = TimeSource.Monotonic.markNow()
            while (!work.isCompleted && mark.elapsedNow() < 40.seconds)
                NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            assertTrue(work.isCompleted)
            runBlocking { work.await() }
        } finally {
            AudioSessionHelper.unbindPlayback(); NowPlayingInfoNotification.unbind()
            host.core.close(); scope.cancel()
        }
    }
}
