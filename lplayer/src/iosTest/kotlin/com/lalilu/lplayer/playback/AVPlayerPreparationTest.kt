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

class AVPlayerPreparationTest {
    @Test fun historyTakeoverKeepsQueueFillWithoutRestartingNativePlayback() = runNativeMainTest {
        val file = PlatformFile(NSTemporaryDirectory() + "lmusic-history-claim-${Random.nextLong()}.wav")
        val engine = AVPlayerEngine()
        try {
            file.sink().buffered().use { it.write(silentWave()) }
            val data = MediaData.Url("file://${file.path}")
            val audio = LAudio(id = "a", mediaSourceName = "validation")
            engine.load(data, audio)
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
                val loading = async(start = CoroutineStart.UNDISPATCHED) { engine.load(data, audio) }
                assertTrue(engine.state.value.isLoading, "Test must cancel an in-flight native load")
                loading.cancelAndJoin()
                assertTrue(loading.isCancelled)
                assertEquals(PlaybackEngineState.EMPTY, engine.state.value)
                delay(200)
                assertEquals(PlaybackEngineState.EMPTY, engine.state.value)
            }
            engine.load(data, audio)
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
                val old = async(start = CoroutineStart.UNDISPATCHED) { engine.load(data, audio) }
                assertTrue(engine.state.value.isLoading, "Old load must still be pending")
                // Same song, distinct AVPlayerItem: checking only the song ID is insufficient.
                engine.load(data, audio)
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

    @Test fun missingLocalFileFailsBeforeLoadReturns() = runNativeMainTest {
        val engine = AVPlayerEngine()
        try {
            assertFailsWith<IllegalStateException> {
                engine.load(
                    MediaData.Url("file:///lmusic-validation-nonexistent/audio.mp3"),
                    LAudio(id = "missing-file", mediaSourceName = "validation"),
                )
            }
            assertNotNull(engine.state.value.error)
            assertFalse(engine.state.value.isLoading)
            assertFalse(engine.state.value.isPlaying)
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
