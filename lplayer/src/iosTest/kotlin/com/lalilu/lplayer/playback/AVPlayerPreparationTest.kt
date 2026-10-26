package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lplayer.extensions.VolumeFadeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.path
import kotlinx.io.buffered
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.math.abs
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import io.ktor.client.engine.mock.respond

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class AVPlayerPreparationTest {
    @Test fun authorizedNativeSuccessorAdvancesWithoutReloadAndCanBeRevoked() = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "native-next-${Random.nextLong()}.wav")
        val engine = AVPlayerEngine()
        val song = LAudio(id = "a", mediaSourceName = "native-next")
        val media = MediaData.Url("file://${file.path}")
        var advanced = 0
        var completed = 0
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            engine.setVolume(0f)
            engine.load(media, song); engine.awaitPreparation()
            engine.onNext = { advanced++ }
            engine.onEvent = { if (it == PlaybackEngineEvent.Completion) completed++ }
            engine.stageNext(media, song.copy(id = "b"))
            delay(300)
            assertEquals(0, advanced)
            engine.seekTo(19_700); engine.play()
            withTimeout(5000) { while (advanced == 0) delay(20) }
            assertEquals(1, advanced)
            assertEquals(0, completed)
            assertTrue(engine.state.value.isPlaying)
            assertTrue(engine.currentPosition() < 3000)
            engine.pause()
            engine.stageNext(media, song.copy(id = "c"))
            engine.cancelNext()
            engine.seekTo(19_700); engine.play()
            withTimeout(5000) { while (completed == 0) delay(20) }
            assertEquals(1, advanced)
            assertEquals(1, completed)
        } finally { engine.release(); file.delete() }
    }

    @Test fun cachedAssetSupportsSeekAndOfflineReopen() = runNativeMainTest {
        val bytes = silentWave()
        val song = LAudio(id = "cached", mediaSourceName = "cached-test")
        val store = com.lalilu.lmedia.domain.source.MediaSourceStateStore().apply { content.ready() }
        val source = object : com.lalilu.lmedia.domain.source.MediaSource {
            override val name = song.mediaSourceName
            override val state = store.state
            override val snapshot = store.snapshot
            override val contentState = store.contentState
            override val dataSource = object : com.lalilu.lmedia.domain.source.MediaDataSource {
                override fun contentCacheScope() = "native-test"
                override fun contentCacheIdentity(song: LAudio) = com.lalilu.lmedia.domain.source.MediaCacheIdentity("native-test", "v1", bytes.size.toLong())
                override suspend fun getMedia(song: LAudio) = MediaData.Url("https://native.test/audio.wav")
            }
        }
        val repository = object : AudioRepository {
            override fun getAudios() = kotlinx.coroutines.flow.flowOf(listOf(song))
            override fun getAudios(ids: List<String>) = getAudios()
            override fun getAudio(id: String) = kotlinx.coroutines.flow.flowOf(song)
            override suspend fun clearUnavailableAudio() {}
        }
        val sources = com.lalilu.lmedia.domain.source.PlatformMediaSource.provide(source)
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val root = NSTemporaryDirectory() + "native-cache-${Random.nextLong()}"
        var requests = 0
        var offline = false
        fun access(): com.lalilu.lplayer.core.NativeContentAccess {
            val client = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { request ->
                check(!offline) { "Offline read must use persisted cache" }
                requests++
                val range = request.headers["Range"]!!.removePrefix("bytes=").split('-').map(String::toInt)
                respond(bytes.copyOfRange(range[0], range[1] + 1), io.ktor.http.HttpStatusCode.PartialContent,
                    io.ktor.http.headersOf("Content-Range", "bytes ${range[0]}-${range[1]}/${bytes.size}"))
            })
            return com.lalilu.lplayer.core.NativeContentAccess(scope, sources, repository, client, "$root/catalog", "$root/content",
                cacheEnabled = { true }, quotaMiB = kotlinx.coroutines.flow.flowOf(64L))
        }
        var content = access()
        var engine = AVPlayerEngine(content)
        try {
            val media = content.resolve(song)
            content.selectCurrent(media)
            engine.load(media, song)
            engine.awaitPreparation()
            assertEquals(20_000, engine.state.value.duration)
            engine.seekTo(12_000)
            assertTrue(abs(engine.currentPosition() - 12_000) < 200)
            withTimeout(10_000) { while (!content.canPreloadAfter(media)) delay(20) }
            engine.release(); content.close()
            val before = requests
            offline = true
            store.content.unavailable("offline")
            content = access()
            engine = AVPlayerEngine(content)
            engine.load(content.resolve(song), song)
            engine.awaitPreparation()
            engine.seekTo(15_000)
            assertTrue(abs(engine.currentPosition() - 15_000) < 200)
            assertEquals(before, requests)
        } finally {
            engine.release(); content.close(); scope.cancel()
            platform.Foundation.NSFileManager.defaultManager.removeItemAtPath(root, null)
        }
    }

    @Test fun sharedCoreControlsNativeSelectionPauseSeekAndOutputFallback() = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "lmusic-p4-${Random.nextLong()}.wav")
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val engine = AVPlayerEngine()
        var playback: com.lalilu.lplayer.core.CorePlayback? = null
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            val songs = listOf("a", "b").map { LAudio(id = it, mediaSourceName = "p4") }
            val repository = object : AudioRepository {
                override fun getAudios() = kotlinx.coroutines.flow.flowOf(songs)
                override fun getAudios(ids: List<String>) = kotlinx.coroutines.flow.flowOf(songs.filter { it.id in ids })
                override fun getAudio(id: String) = kotlinx.coroutines.flow.flowOf(songs.find { it.id == id })
                override suspend fun clearUnavailableAudio() {}
            }
            val source = object : com.lalilu.lmedia.domain.source.MediaSource {
                override val name = "p4"
                override val state = MutableStateFlow<com.lalilu.lmedia.domain.source.SnapshotState>(com.lalilu.lmedia.domain.source.SnapshotState.Idle)
                override val snapshot = MutableStateFlow<com.lalilu.lmedia.domain.source.Snapshot?>(null)
                override val contentState = MutableStateFlow(com.lalilu.lmedia.domain.source.MediaContentState(com.lalilu.lmedia.domain.source.MediaContentAvailability.Ready))
                override val dataSource = object : com.lalilu.lmedia.domain.source.MediaDataSource {
                    override suspend fun getMedia(song: LAudio) = MediaData.Url("file://${file.path}")
                }
            }
            val runtime = com.lalilu.lplayer.core.EngineAudioRuntime(scope, listOf(engine),
                volume = { _, value -> engine.setVolume(value) }, canFade = { true })
            val host = com.lalilu.lplayer.core.CorePlayback(scope, runtime,
                com.lalilu.lmedia.domain.source.PlatformMediaSource.provide(source), repository,
                PlaybackHistoryImpl(object : HistoryStorage {
                    override fun savedPosition() = 0L
                    override fun savePosition(position: Long) {}
                }))
            playback = host
            host.updatePlaylist(songs, 0, false)
            withTimeout(10_000) { host.core.state.first { it.phase == com.lalilu.playback.core.Phase.ReadyPaused } }
            host.seekTo(4000)
            withTimeout(5_000) { host.core.state.first { it.pendingSeekId == null && it.positionMs >= 3900 } }
            host.playAudio(songs[0])
            assertFalse(host.isPlaying.value)
            assertTrue(host.currentPosition() >= 3900)
            host.play()
            withTimeout(5_000) { host.core.state.first { it.phase == com.lalilu.playback.core.Phase.Playing && it.positionMs > 4100 } }
            host.pause()
            host.skipToNext()
            withTimeout(5_000) { host.core.state.first { it.selected?.id == "b" && it.phase == com.lalilu.playback.core.Phase.ReadyPaused } }
            assertFalse(engine.state.value.isPlaying)
            host.skipToNext()
            withTimeout(5_000) { host.core.state.first { it.selected?.id == "a" && it.phase == com.lalilu.playback.core.Phase.ReadyPaused } }
            host.seekTo(19_000)
            withTimeout(5_000) { host.core.state.first { it.pendingSeekId == null && it.positionMs >= 18_900 } }
            host.play()
            withTimeout(5_000) { host.core.state.first { it.selected?.id == "b" && it.naturalTransition && it.phase == com.lalilu.playback.core.Phase.Playing } }
            assertTrue(host.currentPosition() < 3000)
            assertEquals(songs[1].playbackId, host.currentEngineItemKey.value)
            assertEquals(songs, host.queue.stateSnapshot().list)
            host.pause()
            com.lalilu.lplayer.helper.IosOutputProbe.start()
            val output = com.lalilu.lplayer.core.PlaybackOutput.capabilities.value
            assertEquals(com.lalilu.playback.core.OutputMode.Experience, output.actualMode(com.lalilu.playback.core.OutputMode.Original))
            assertNotNull(output.fallbackReason(com.lalilu.playback.core.OutputMode.Original))
        } finally {
            playback?.core?.close()
            engine.release()
            scope.cancel()
            file.delete()
        }
    }

    @Test fun historyTakeoverKeepsQueueFillWithoutRestartingNativePlayback() = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "lmusic-history-claim-${Random.nextLong()}.wav")
        val engine = AVPlayerEngine()
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            val data = MediaData.Url("file://${file.path}")
            val audio = LAudio(id = "a", mediaSourceName = "validation")
            engine.load(data, audio)
            engine.awaitPreparation()
            engine.play()
            supervisorScope {
                val rows = MutableStateFlow(listOf(audio))
                val repository = object : AudioRepository {
                    override fun getAudios() = rows
                    override fun getAudios(ids: List<String>) = rows.map { list -> list.filter { it.id in ids } }
                    override fun getAudio(id: String) = rows.map { list -> list.firstOrNull { it.id == id } }
                    override suspend fun clearUnavailableAudio() = Unit
                }
                val restorer = HistoryQueueRestorer(
                    PlaybackHistory.HistorySnapshot(listOf("a", "b"), 0, 12000), repository)
                val commands = LatestPlaybackCommand()
                val queue = PlayableQueueImpl()
                val entered = CompletableDeferred<Unit>()
                val resolved = CompletableDeferred<Unit>()
                var deliveries = 0
                restorer.start(this, queue) {
                    deliveries++
                    commands.run {
                        entered.complete(Unit)
                        resolved.await()
                        engine.load(data, audio)
                        engine.play()
                    }
                }
                entered.await()
                restorer.claimPlaybackControl()
                commands.run { engine.pause() }
                resolved.complete(Unit)
                rows.value = listOf(audio, audio.copy(id = "b"))
                withTimeout(5_000) { restorer.state.first { it is HistoryRestoreState.Complete } }
                assertEquals(1, deliveries)
                assertEquals(listOf("a", "b"), queue.stateSnapshot().list.map { it.id })
                assertFalse(engine.state.value.isPlaying)
                val position = engine.currentPosition()
                delay(300)
                assertTrue(abs(engine.currentPosition() - position) <= 50)
            }
        } finally {
            engine.release()
            file.delete()
        }
    }

    @Test fun pauseCommandPreventsLateResolutionFromRestartingNativePlayback() = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "lmusic-command-${Random.nextLong()}.wav")
        val engine = AVPlayerEngine()
        val commands = LatestPlaybackCommand()
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            val data = MediaData.Url("file://${file.path}")
            val audio = LAudio(id = "command-wave", mediaSourceName = "validation")
            engine.load(data, audio)
            engine.awaitPreparation()
            engine.play()
            supervisorScope {
                val resolved = CompletableDeferred<Unit>()
                val loading = async(start = CoroutineStart.UNDISPATCHED) {
                    commands.run { resolved.await(); engine.load(data, audio); engine.play() }
                }
                commands.run {
                    val fade = VolumeFadeHelper(onSetVolume = engine::setVolume)
                    fade.updateVolume(100f)
                    fade.pauseAndAwait { engine.pause() }
                }
                resolved.complete(Unit)
                assertFailsWith<CancellationException> { loading.await() }
                assertFalse(engine.state.value.isPlaying)
                val position = engine.currentPosition()
                delay(300)
                assertTrue(abs(engine.currentPosition() - position) <= 50)
            }
        } finally {
            engine.release()
            file.delete()
        }
    }

    @Test fun pauseDuringPreparationCancelsQueuedAutoplay() = assertPreparationStaysPaused(stop = false)

    @Test fun stopDuringPreparationCancelsQueuedAutoplay() = assertPreparationStaysPaused(stop = true)

    private fun assertPreparationStaysPaused(stop: Boolean) = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "lmusic-pending-play-${Random.nextLong()}.wav")
        val engine = AVPlayerEngine()
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            supervisorScope {
                val loading = async(start = CoroutineStart.UNDISPATCHED) {
                    engine.load(
                        MediaData.Url("file://${file.path}"),
                        LAudio(id = "pending-play", mediaSourceName = "validation"),
                    )
                    engine.awaitPreparation()
                }
                assertTrue(engine.state.value.isLoading, "Control must arrive before native preparation finishes")
                engine.play()
                if (stop) engine.stop() else engine.pause()
                loading.await()
                assertFalse(engine.state.value.isLoading)
                assertNull(engine.state.value.error)
                assertFalse(engine.state.value.isPlaying)
                val position = engine.currentPosition()
                delay(300)
                assertFalse(engine.state.value.isPlaying)
                assertTrue(abs(engine.currentPosition() - position) <= 50)
            }
        } finally {
            engine.release()
            file.delete()
        }
    }

    @Test fun cancelledLoadReleasesOnlyItsItemAndCanBeRetried() = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "lmusic-cancel-${Random.nextLong()}.wav")
        val engine = AVPlayerEngine()
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            val data = MediaData.Url("file://${file.path}")
            val audio = LAudio(id = "cancel-wave", mediaSourceName = "validation")
            supervisorScope {
                val loading = async(start = CoroutineStart.UNDISPATCHED) {
                    engine.load(data, audio)
                    engine.awaitPreparation()
                }
                assertTrue(engine.state.value.isLoading, "Test must cancel an in-flight native load")
                loading.cancelAndJoin()
                assertTrue(loading.isCancelled)
                assertEquals(PlaybackEngineState.EMPTY, engine.state.value)
                delay(200)
                assertEquals(PlaybackEngineState.EMPTY, engine.state.value)
            }
            engine.load(data, audio)
            engine.awaitPreparation()
            assertFalse(engine.state.value.isLoading)
            assertNull(engine.state.value.error)
            assertTrue(engine.state.value.duration in 19_900L..20_100L)
        } finally {
            engine.release()
            file.delete()
        }
    }

    @Test fun replacedLoadCannotReleaseTheNewerItem() = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "lmusic-replace-${Random.nextLong()}.wav")
        val engine = AVPlayerEngine()
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            val data = MediaData.Url("file://${file.path}")
            val audio = LAudio(id = "replace-wave", mediaSourceName = "validation")
            supervisorScope {
                val old = async(start = CoroutineStart.UNDISPATCHED) {
                    engine.load(data, audio)
                    engine.awaitPreparation()
                }
                assertTrue(engine.state.value.isLoading, "Old load must still be pending")
                // Same song, distinct AVPlayerItem: checking only the song ID is insufficient.
                engine.load(data, audio)
                engine.awaitPreparation()
                assertFailsWith<CancellationException> { old.await() }
                assertNull(engine.state.value.error)
                assertFalse(engine.state.value.isLoading)
                engine.seekTo(12_000)
                withTimeout(5_000) {
                    while (abs(engine.currentPosition() - 12_000) > 100) delay(20)
                }
            }
        } finally {
            engine.release()
            file.delete()
        }
    }

    @Test fun localAudioPreparesSeeksPlaysAndAwaitsFadePause() = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "lmusic-native-${Random.nextLong()}.wav")
        val engine = AVPlayerEngine()
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            engine.load(
                MediaData.Url("file://${file.path}"),
                LAudio(id = "local-wave", mediaSourceName = "validation"),
            )
            engine.awaitPreparation()
            assertFalse(engine.state.value.isLoading)
            assertTrue(engine.state.value.duration in 19_900L..20_100L)
            assertFalse(engine.state.value.isPlaying)
            engine.seekTo(12_000)
            withTimeout(5_000) {
                while (abs(engine.currentPosition() - 12_000) > 100) delay(20)
            }
            assertFalse(engine.state.value.isPlaying)
            engine.pause()
            val pausedAt = engine.currentPosition()
            delay(200)
            assertTrue(abs(engine.currentPosition() - pausedAt) <= 10)
            engine.play()
            withTimeout(5_000) {
                while (engine.currentPosition() < pausedAt + 200) delay(20)
            }
            val fade = VolumeFadeHelper(onSetVolume = engine::setVolume)
            fade.updateVolume(100f)
            fade.pauseAndAwait { engine.pause() }
            val stoppedAt = engine.currentPosition()
            delay(200)
            assertFalse(engine.state.value.isPlaying)
            assertTrue(abs(engine.currentPosition() - stoppedAt) <= 50)
        } finally {
            engine.release()
            file.delete()
        }
    }

    @Test fun aMissingLocalFileIsReportedThroughStateWithoutBlockingTheSelection() = runNativeMainTest {
        val engine = AVPlayerEngine()
        try {
            val started = TimeSource.Monotonic.markNow()
            engine.load(
                MediaData.Url("file:///lmusic-validation-nonexistent/audio.mp3"),
                LAudio(id = "missing-file", mediaSourceName = "validation"),
            )
            // 选曲命令不再等待原生加载：失败由状态流交给外层观察者记账与导航。
            assertTrue(started.elapsedNow() < 2.seconds, "selection must not wait for native preparation")
            withTimeout(15.seconds) {
                engine.state.first { it.error != null || !it.isLoading }
            }
            assertNotNull(engine.state.value.error)
            assertFalse(engine.state.value.isLoading)
            assertFalse(engine.state.value.isPlaying)
        } finally {
            engine.release()
        }
    }

    @Test fun aSeekIssuedRightAfterANonBlockingSelectionStillLands() = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "lmusic-seek-while-loading-${Random.nextLong()}.wav")
        val engine = AVPlayerEngine()
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            // 选曲不再等待就绪：调用方紧接着定位时，AVFoundation 必须自己排队并在就绪后落到目标位置。
            engine.load(MediaData.Url("file://${file.path}"), LAudio(id = "seek-loading", mediaSourceName = "validation"))
            engine.seekTo(12_000)
            withTimeout(10.seconds) {
                while (abs(engine.currentPosition() - 12_000) > 200) delay(20)
            }
            assertNull(engine.state.value.error)
            engine.pause()
        } finally {
            engine.release()
            file.delete()
        }
    }

    @Test fun aSlowRemoteLoadDoesNotBlockTheSelectionCommand() = runNativeMainTest {
        val engine = AVPlayerEngine()
        try {
            val started = TimeSource.Monotonic.markNow()
            // 不可路由地址：修复前这里会等待原生 30s 上限并把点歌一起阻塞。
            engine.load(
                MediaData.Url("http://10.255.255.1/lmusic-slow-probe.mp3"),
                LAudio(id = "slow-remote", mediaSourceName = "validation"),
            )
            assertTrue(started.elapsedNow() < 2.seconds, "selection must not wait for native preparation")
        } finally {
            engine.release()
        }
    }

    private fun runNativeMainTest(block: suspend () -> Unit) {
        // Native's CLI runner occupies main; pump its run loop instead of blocking Main
        // in runTest. AVFoundation/KVO and Dispatchers.Main both need that loop alive.
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val result = scope.async { block() }
        try {
            val started = TimeSource.Monotonic.markNow()
            while (!result.isCompleted && started.elapsedNow() < 45.seconds) {
                NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            }
            assertTrue(result.isCompleted, "AVPlayer preparation test did not finish in 45 seconds")
            runBlocking { result.await() }
        } finally {
            scope.cancel()
        }
    }

    /** Self-contained 20-second, mono 16-bit PCM WAV; no external music fixture. */
    private fun silentWave(): ByteArray {
        val samples = 8_000 * 20
        val bytes = ByteArray(44 + samples * 2)
        fun ascii(offset: Int, value: String) { value.encodeToByteArray().copyInto(bytes, offset) }
        fun littleEndian(offset: Int, value: Int, size: Int) {
            repeat(size) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
        }
        ascii(0, "RIFF")
        littleEndian(4, bytes.size - 8, 4)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        littleEndian(16, 16, 4)
        littleEndian(20, 1, 2)
        littleEndian(22, 1, 2)
        littleEndian(24, 8_000, 4)
        littleEndian(28, 16_000, 4)
        littleEndian(32, 2, 2)
        littleEndian(34, 16, 2)
        ascii(36, "data")
        littleEndian(40, samples * 2, 4)
        return bytes
    }
}
