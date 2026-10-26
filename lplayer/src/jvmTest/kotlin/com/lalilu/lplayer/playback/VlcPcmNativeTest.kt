package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lplayer.player.VLCPlayerLoader
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.*

class VlcPcmNativeTest {
    @Test fun unavailablePcmOutputFallsBackToNativePlayback() = runBlocking {
        val resources = System.getenv("LMUSIC_NATIVE_RESOURCES")
        val fixture = System.getenv("LMUSIC_NATIVE_AUDIO_FIXTURE")
        assumeTrue(resources != null && fixture != null)
        System.setProperty("compose.application.resources.dir", resources!!)
        VLCPlayerLoader.initialize().join()
        val factory = MediaPlayerFactory(null as NativeDiscovery?, "--no-video", "--aout=dummy")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        val player = factory.mediaPlayers().newMediaPlayer()
        val provider: suspend () -> uk.co.caprica.vlcj.player.base.MediaPlayer = { player }
        val engine = VlcPcmEngine(scope, provider,
            sinkFactory = { throw javax.sound.sampled.LineUnavailableException("Test unavailable output") },
            fallbackFactory = { VlcEngine(scope, provider) })
        try {
            engine.load(MediaData.Url(fixture!!), LAudio("fallback", mediaSourceName = "test"))
            engine.awaitPreparation()
            assertFalse(engine.continuousOutputAvailable)
            engine.play()
            withTimeout(5000) { while (engine.currentPosition() < 300) delay(20) }
            engine.pause()
            withTimeout(3000) { while (engine.state.value.isPlaying) delay(20) }
            assertFalse(engine.state.value.isPlaying)
        } finally { engine.release(); player.release(); scope.cancel(); factory.release() }
    }

    @Test fun sharedCoreOwnsNaturalAdvanceAndPausedManualSelection() = runBlocking {
        val resources = System.getenv("LMUSIC_NATIVE_RESOURCES")
        assumeTrue(resources != null)
        System.setProperty("compose.application.resources.dir", resources!!)
        VLCPlayerLoader.initialize().join()
        val factory = MediaPlayerFactory(null as NativeDiscovery?, "--no-video")
        val directory = Files.createTempDirectory("vlc-core-pcm").toFile()
        val file = directory.resolve("audio.wav").apply { writeBytes(wave(4096)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        val engine = VlcPcmEngine(scope, { factory.mediaPlayers().newMediaPlayer() }, sinkFactory = { RecordingSink(10) })
        val runtime = com.lalilu.lplayer.core.EngineAudioRuntime(scope, listOf(engine),
            volume = { _, value -> engine.setVolume(value) }, canFade = { true })
        val core = com.lalilu.playback.core.PlaybackCore(scope, com.lalilu.playback.core.ContentResolver { key ->
            runtime.resource(MediaData.Url(file.path), LAudio(key.id, mediaSourceName = key.source))
        }, runtime)
        val songs = listOf("a", "b").map { com.lalilu.playback.core.TrackKey("native", it) }
        try {
            core.dispatch(com.lalilu.playback.core.PlaybackCommand.ReplaceQueue(songs, songs[0], true))
            withTimeout(10000) { while (core.state.value.selected != songs[1] || !core.state.value.naturalTransition) delay(10) }
            assertEquals(songs, core.state.value.queue)
            assertTrue(core.state.value.positionMs < 500)
            core.dispatch(com.lalilu.playback.core.PlaybackCommand.Pause)
            withTimeout(3000) { while (engine.state.value.isPlaying) delay(10) }
            core.dispatch(com.lalilu.playback.core.PlaybackCommand.Select(songs[0]))
            withTimeout(10000) { while (core.state.value.phase != com.lalilu.playback.core.Phase.ReadyPaused) delay(10) }
            assertEquals(songs[0], core.state.value.selected)
            assertFalse(engine.state.value.isPlaying)
            assertEquals(com.lalilu.playback.core.PlayIntent.Pause, core.state.value.intent)
        } finally { core.close(); delay(100); engine.release(); scope.cancel(); factory.release(); directory.deleteRecursively() }
    }

    @Test fun systemOutputKeepsPauseAndSeekIntentAndReleasesNativeThreads() = runBlocking {
        val resources = System.getenv("LMUSIC_NATIVE_RESOURCES")
        val fixture = System.getenv("LMUSIC_NATIVE_AUDIO_FIXTURE")
        assumeTrue(resources != null && fixture != null)
        System.setProperty("compose.application.resources.dir", resources!!)
        VLCPlayerLoader.initialize().join()
        val factory = MediaPlayerFactory(null as NativeDiscovery?, "--no-video")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        val engine = VlcPcmEngine(scope, { factory.mediaPlayers().newMediaPlayer() })
        try {
            engine.load(MediaData.Url(fixture!!), LAudio("a", mediaSourceName = "test"))
            engine.setVolume(0f)
            engine.awaitPreparation()
            assertEquals(0L, engine.currentPosition())
            engine.play()
            withTimeout(5000) { while (engine.currentPosition() < 300) delay(20) }
            engine.pause()
            val paused = engine.currentPosition()
            delay(200)
            assertEquals(paused, engine.currentPosition())
            engine.seekTo(1500)
            assertTrue(kotlin.math.abs(engine.currentPosition() - 1500) < 100)
            assertFalse(engine.state.value.isPlaying)
            engine.play()
            withTimeout(5000) { while (engine.currentPosition() < 1700) delay(20) }
            engine.pause()
        } finally { engine.release(); scope.cancel(); factory.release() }
        withTimeout(5000) { while (Thread.getAllStackTraces().keys.any { it.isAlive && it.name == "LMusic-PCM-output" }) delay(20) }
    }

    private class RecordingSink(private val delayMs: Long = 2) : PcmSink {
        val bytes = ByteArrayOutputStream()
        var opens = 1
        var closed = false
        override val playedFrames: Long get() = synchronized(bytes) { bytes.size().toLong() / PCM_FRAME_BYTES }
        override fun write(bytes: ByteArray) { synchronized(this.bytes) { this.bytes.write(bytes) }; Thread.sleep(delayMs) }
        override fun start() {}
        override fun pause() {}
        override fun flush() {}
        override fun close() { closed = true }
        fun result() = synchronized(bytes) { bytes.toByteArray() }
    }
    @Test fun twoNativeDecodersProduceOneContiguousSampleStream() = runBlocking {
        val resources = System.getenv("LMUSIC_NATIVE_RESOURCES")
        assumeTrue(resources != null)
        System.setProperty("compose.application.resources.dir", resources!!)
        VLCPlayerLoader.initialize().join()
        val factory = MediaPlayerFactory(null as NativeDiscovery?, "--no-video")
        val directory = Files.createTempDirectory("vlc-pcm-test").toFile()
        val first = directory.resolve("a.wav").apply { writeBytes(wave(8192)) }
        val second = directory.resolve("b.wav").apply { writeBytes(wave(-8192)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        val sink = RecordingSink()
        var outputCount = 0
        val engine = VlcPcmEngine(scope, { factory.mediaPlayers().newMediaPlayer() }, sinkFactory = { outputCount++; sink })
        val advanced = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        try {
            engine.load(MediaData.Url(first.path), LAudio("a", mediaSourceName = "test"))
            engine.setVolume(1f)
            engine.awaitPreparation()
            delay(100) // A short input may already be waiting in native drain while output is paused.
            engine.seekTo(250)
            assertEquals(250L, engine.currentPosition())
            engine.seekTo(0)
            engine.onNext = { advanced.complete(Unit) }
            engine.onEvent = { if (it == PlaybackEngineEvent.Completion) completed.complete(Unit) }
            engine.stageNext(MediaData.Url(second.path), LAudio("b", mediaSourceName = "test"))
            delay(300)
            assertTrue(sink.result().isEmpty(), "Preparation must remain silent")
            engine.play()
            withTimeout(10000) { advanced.await(); completed.await() }
            val output = sink.result()
            println("PCM_CONTINUITY bytes=${output.size} expected=${PCM_RATE * PCM_FRAME_BYTES * 2} outputs=$outputCount")
            assertEquals(PCM_RATE * PCM_FRAME_BYTES * 2, output.size)
            assertEquals(1, outputCount)
            for (i in output.indices step 2) {
                val sample = ((output[i].toInt() and 255) or (output[i + 1].toInt() shl 8)).toShort().toInt()
                val expected = if (i < PCM_RATE * PCM_FRAME_BYTES) 8192 else -8192
                assertTrue(kotlin.math.abs(sample - expected) <= 1, "Unexpected sample at byte $i: $sample (wanted $expected)")
            }
        } finally {
            engine.release(); scope.cancel(); factory.release(); directory.deleteRecursively()
        }
        assertTrue(sink.closed)
    }
    private fun wave(sample: Int): ByteArray {
        val bytes = ByteArray(44 + PCM_RATE * PCM_FRAME_BYTES)
        fun ascii(offset: Int, value: String) { value.encodeToByteArray().copyInto(bytes, offset) }
        fun le(offset: Int, value: Int, count: Int) { repeat(count) { bytes[offset + it] = (value ushr (it * 8)).toByte() } }
        ascii(0, "RIFF"); le(4, bytes.size - 8, 4); ascii(8, "WAVEfmt "); le(16, 16, 4)
        le(20, 1, 2); le(22, 2, 2); le(24, PCM_RATE, 4); le(28, PCM_RATE * PCM_FRAME_BYTES, 4)
        le(32, PCM_FRAME_BYTES, 2); le(34, 16, 2); ascii(36, "data"); le(40, bytes.size - 44, 4)
        for (i in 44 until bytes.size step 2) le(i, sample, 2)
        return bytes
    }
}
