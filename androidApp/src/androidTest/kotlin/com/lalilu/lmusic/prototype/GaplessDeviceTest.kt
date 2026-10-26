package com.lalilu.lmusic.prototype

import android.content.Context
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lplayer.core.Media3CoreRuntime
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/** Measures PCM after Media3 trimming and observes the actual AudioTrack lifecycle. */
class GaplessDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val a = TrackKey("gapless", "a")
    private val b = TrackKey("gapless", "b")
    private val c = TrackKey("gapless", "c")
    private class Probe(private val rate: Int = 16000, private val channels: Int = 1) : TeeAudioProcessor.AudioBufferSink, AnalyticsListener {
        val pcm = ByteArrayOutputStream()
        val tracks = AtomicInteger()
        val releases = AtomicInteger()
        val underruns = AtomicInteger()
        val successorLoads = AtomicInteger()
        override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo, mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData) {
            if (loadEventInfo.uri.lastPathSegment?.startsWith("b.") == true) successorLoads.incrementAndGet()
        }
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            check(sampleRateHz == rate && channelCount == channels && encoding == C.ENCODING_PCM_16BIT)
        }
        override fun handleBuffer(buffer: ByteBuffer) { val data=ByteArray(buffer.remaining()); buffer.get(data); synchronized(pcm) { pcm.write(data) } }
        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) { tracks.incrementAndGet() }
        override fun onAudioTrackReleased(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) { releases.incrementAndGet() }
        override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) { underruns.incrementAndGet() }
        fun bytes() = synchronized(pcm) { pcm.toByteArray() }
    }
    private fun renderer(probe: Probe) = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(TeeAudioProcessor(probe))).build()
    }
    private suspend fun PlaybackCore.command(value: PlaybackCommand) = withContext(Dispatchers.Main) { dispatch(value) }
    private suspend fun PlaybackCore.await(predicate: (PlaybackSnapshot)->Boolean) = withTimeout(15000) { state.first(predicate) }
    private suspend fun waitFor(predicate: ()->Boolean) = withTimeout(15000) { while (!withContext(Dispatchers.Main) { predicate() }) delay(20) }
    private fun wave(root: File, name: String, value: Short, ms: Int): File {
        val file=PrototypeAudio.write(root,name,durationMs=ms)
        val data=file.readBytes()
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).apply { position(44); while(hasRemaining()) putShort(value) }
        file.writeBytes(data)
        return file
    }

    @Test fun preloadsPrivatelyAndNaturalBoundaryHasExactPcmAndOneAudioTrack() = measureBoundary()
    @Test fun wavToFlacKeepsExactSamplesAndTheSameAudioTrack() = measureBoundary("constant-16k.flac")
    @Test fun wavToMp3HonorsEncoderDelayAndPaddingWithoutRecreatingAudioTrack() = measureBoundary("tone-16k.mp3")

    private fun measureBoundary(asset: String? = null) = runBlocking<Unit> {
        val root=File(context.cacheDir,"p3-pcm-${System.nanoTime()}")
        val first=wave(root,"a",1000,2000)
        val reference=wave(root,"reference",2000,1000)
        val tail=wave(root,"c",3000,1000)
        val hasTail=asset?.endsWith("mp3")==true
        val second=if(asset==null) wave(root,"b",2000,1000) else File(root,"b.${asset.substringAfterLast('.')}").apply {
            InstrumentationRegistry.getInstrumentation().context.assets.open("p3/$asset").use { input -> outputStream().use { input.copyTo(it) } }
        }
        val probe=Probe(); val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var runtime: Media3CoreRuntime; lateinit var core: PlaybackCore
        try {
            withContext(Dispatchers.Main) {
                runtime=Media3CoreRuntime(context,gain=0.05f,renderersFactory=renderer(probe))
                runtime.player.addAnalyticsListener(probe)
                core=PlaybackCore(parent,ContentResolver { PlayableResource((when(it) { a -> first; b -> second; else -> tail }).toURI().toString()) },runtime)
            }
            core.command(PlaybackCommand.ReplaceQueue(if(hasTail) listOf(a,b,c) else listOf(a,b)))
            core.await { it.phase==Phase.ReadyPaused }
            waitFor { runtime.player.mediaItemCount==2 && probe.successorLoads.get()>0 }
            assertEquals(a,core.state.value.selected)
            assertEquals(if(hasTail) listOf(a,b,c) else listOf(a,b),core.state.value.queue)
            core.command(PlaybackCommand.Play)
            core.await { it.selected==b && it.phase==Phase.Playing }
            assertTrue(core.state.value.naturalTransition)
            core.await { it.phase==Phase.Ended }
            val expected=first.readBytes().copyOfRange(44,first.length().toInt())+reference.readBytes().copyOfRange(44,reference.length().toInt())+(if(hasTail) tail.readBytes().drop(44).toByteArray() else byteArrayOf())
            if(asset?.endsWith("mp3")==true) {
                assertEquals("Encoded delay and padding must not add decoded samples",expected.size,probe.bytes().size)
                assertArrayEquals(expected.copyOfRange(0,64000),probe.bytes().copyOfRange(0,64000))
            } else assertArrayEquals("No missing, duplicated, or inserted PCM samples at the boundary",expected,probe.bytes())
            println("P3 boundary ${asset ?: "wav"}: PCM bytes=${probe.bytes().size}, AudioTracks=${probe.tracks.get()}, releases=${probe.releases.get()}, underruns=${probe.underruns.get()}, successor loads=${probe.successorLoads.get()}")
            assertEquals("Same AudioTrack across the boundary",1,probe.tracks.get())
            assertEquals(0,probe.releases.get())
            assertEquals(0,probe.underruns.get())
        } finally { withContext(Dispatchers.Main) { core.close(); parent.cancel() }; root.deleteRecursively() }
    }

    @Test fun invalidatedNextNeverPlaysAndPausedPreparationStaysSilent() = runBlocking<Unit> {
        val root=File(context.cacheDir,"p3-cancel-${System.nanoTime()}")
        val files=mapOf(a to wave(root,"a",1000,2500),b to wave(root,"b",2000,1000),c to wave(root,"c",3000,1000))
        val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var runtime: Media3CoreRuntime; lateinit var core: PlaybackCore
        val seen=mutableListOf<TrackKey>()
        try {
            withContext(Dispatchers.Main) {
                runtime=Media3CoreRuntime(context,gain=0.05f)
                core=PlaybackCore(parent,ContentResolver { PlayableResource(files.getValue(it).toURI().toString()) },runtime)
            }
            val observer=launch { core.state.collect { if(it.phase==Phase.Playing) seen += it.selected!! } }
            core.command(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
            core.await { it.phase==Phase.ReadyPaused }; waitFor { runtime.player.mediaItemCount==2 }
            core.command(PlaybackCommand.PlayNext(c))
            waitFor { runtime.player.getMediaItemAt(1).mediaId.endsWith("c") }
            core.command(PlaybackCommand.Play)
            core.await { it.positionMs>100 }
            core.command(PlaybackCommand.Pause)
            delay(2800)
            assertEquals(a,core.state.value.selected)
            assertEquals(PlayIntent.Pause,core.state.value.intent)
            assertFalse(withContext(Dispatchers.Main) { runtime.player.isPlaying })
            core.command(PlaybackCommand.Play)
            core.await { it.selected==c && it.phase==Phase.Playing }
            assertFalse(seen.contains(b))
            observer.cancel()
        } finally { withContext(Dispatchers.Main) { core.close(); parent.cancel() }; root.deleteRecursively() }
    }

    @Test fun failedNativeCandidateDoesNotInterruptCurrentAudio() = runBlocking<Unit> {
        val root=File(context.cacheDir,"p3-fail-${System.nanoTime()}")
        val first=wave(root,"a",1000,2500); val last=wave(root,"c",3000,1000)
        val probe=Probe()
        val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var core: PlaybackCore
        try {
            withContext(Dispatchers.Main) {
                core=PlaybackCore(parent,ContentResolver { PlayableResource(when(it) { a->first; c->last; else->File(root,"missing.wav") }.toURI().toString()) },Media3CoreRuntime(context,gain=0.05f,renderersFactory=renderer(probe)))
            }
            core.command(PlaybackCommand.ReplaceQueue(listOf(a,b,c))); core.command(PlaybackCommand.Play)
            core.await { it.selected==a && it.positionMs>1500 }
            core.await { it.selected==c && it.phase==Phase.Playing }
            core.await { it.phase==Phase.Ended }
            assertArrayEquals(first.readBytes().drop(44).toByteArray()+last.readBytes().drop(44).toByteArray(),probe.bytes())
        } finally { withContext(Dispatchers.Main) { core.close(); parent.cancel() }; root.deleteRecursively() }
    }
    @Test fun unresolvedCandidateWaitsSilentlyAndHonorsPauseAndNewSelection() = runBlocking<Unit> {
        val root=File(context.cacheDir,"p3-wait-${System.nanoTime()}")
        val first=wave(root,"a",1000,600); val other=wave(root,"other",2000,1000)
        val gate=CompletableDeferred<Unit>()
        val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var core: PlaybackCore; lateinit var runtime: Media3CoreRuntime
        try {
            withContext(Dispatchers.Main) {
                runtime=Media3CoreRuntime(context,gain=0.05f)
                core=PlaybackCore(parent,ContentResolver { if(it==b) gate.await(); PlayableResource((if(it==a) first else other).toURI().toString()) },runtime)
            }
            core.command(PlaybackCommand.ReplaceQueue(listOf(a,b,c))); core.command(PlaybackCommand.Play)
            core.await { it.selected==b && it.phase==Phase.Preparing }
            delay(200)
            assertFalse(withContext(Dispatchers.Main) { runtime.player.isPlaying })
            core.command(PlaybackCommand.Pause)
            core.command(PlaybackCommand.Select(c))
            gate.complete(Unit)
            core.await { it.selected==c && it.phase==Phase.ReadyPaused }
            delay(200)
            assertFalse(withContext(Dispatchers.Main) { runtime.player.isPlaying })
            assertEquals(c,core.state.value.selected)
        } finally { withContext(Dispatchers.Main) { core.close(); parent.cancel() }; root.deleteRecursively() }
    }

    @Test fun productionOutputNormalizesDifferentSampleRatesWithoutSinkRecreation() = runBlocking<Unit> {
        val root=File(context.cacheDir,"p3-rates-${System.nanoTime()}")
        val first=wave(root,"a",1000,2000)
        val second=File(root,"b.wav").apply {
            InstrumentationRegistry.getInstrumentation().context.assets.open("p3/constant-44100-stereo.wav").use { input -> outputStream().use { input.copyTo(it) } }
        }
        val probe=Probe(48000,2); val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var core: PlaybackCore
        try {
            withContext(Dispatchers.Main) {
                val runtime=Media3CoreRuntime(context,gain=0.05f,renderersFactory=com.lalilu.lplayer.core.ContinuousAudioRenderersFactory(context,probe))
                runtime.player.addAnalyticsListener(probe)
                core=PlaybackCore(parent,ContentResolver { PlayableResource((if(it==a) first else second).toURI().toString()) },runtime)
            }
            core.command(PlaybackCommand.ReplaceQueue(listOf(a,b))); core.command(PlaybackCommand.Play)
            core.await { it.phase==Phase.Ended }
            assertEquals("3 seconds of stereo 48 kHz output",48000*3*2*2,probe.bytes().size)
            assertEquals(1,probe.tracks.get())
            assertEquals(0,probe.releases.get())
            assertEquals(0,probe.underruns.get())
            val samples=ByteBuffer.wrap(probe.bytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            // No silent output inserted around the rate/channel-format transition (resampling changes samples).
            for (sample in (48000*2-16)*2 until (48000*2+16)*2) assertTrue(samples.get(sample).toInt()>0)
            println("P3 normalized 16k mono -> 44.1k stereo: bytes=${probe.bytes().size}, AudioTracks=${probe.tracks.get()}, underruns=${probe.underruns.get()}")
        } finally { withContext(Dispatchers.Main) { core.close(); parent.cancel() }; root.deleteRecursively() }
    }

    @Test fun singleTrackLoopKeepsExactPcmAndNeverExpandsTheBusinessQueue() = runBlocking<Unit> {
        val root=File(context.cacheDir,"p3-repeat-${System.nanoTime()}")
        val file=wave(root,"a",1000,1000)
        val probe=Probe(); val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var core: PlaybackCore; lateinit var runtime: Media3CoreRuntime
        try {
            withContext(Dispatchers.Main) {
                runtime=Media3CoreRuntime(context,gain=0.05f,renderersFactory=renderer(probe))
                runtime.player.addAnalyticsListener(probe)
                core=PlaybackCore(parent,ContentResolver { PlayableResource(file.toURI().toString()) },runtime)
            }
            core.command(PlaybackCommand.ReplaceQueue(listOf(a)))
            core.command(PlaybackCommand.SetMode(PlayMode.RepeatOne))
            core.command(PlaybackCommand.Play)
            core.await { it.epoch>=3 && it.phase==Phase.Playing }
            val pcm=file.readBytes().drop(44).toByteArray()
            assertArrayEquals(pcm+pcm,probe.bytes().copyOfRange(0,pcm.size*2))
            assertEquals(listOf(a),core.state.value.queue)
            assertTrue(withContext(Dispatchers.Main) { runtime.player.mediaItemCount<=2 })
            assertEquals(1,probe.tracks.get())
            assertEquals(0,probe.releases.get())
            assertEquals(0,probe.underruns.get())
        } finally { withContext(Dispatchers.Main) { core.close(); parent.cancel() }; root.deleteRecursively() }
    }

    @Test fun nativeReadDelayAtBoundarySelectsWaitingSongAndPauseCancelsAutoplay() = runBlocking<Unit> {
        val root=File(context.cacheDir,"p3-native-wait-${System.nanoTime()}")
        val first=wave(root,"a",1000,600); val second=wave(root,"b",2000,1000)
        val gate=java.util.concurrent.CountDownLatch(1)
        val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var core: PlaybackCore; lateinit var runtime: Media3CoreRuntime
        val factory=androidx.media3.datasource.DataSource.Factory {
            val delegate=androidx.media3.datasource.FileDataSource()
            object: androidx.media3.datasource.DataSource by delegate {
                override fun open(spec: androidx.media3.datasource.DataSpec): Long {
                    if(spec.uri.lastPathSegment=="b.wav") {
                        try { check(gate.await(10,java.util.concurrent.TimeUnit.SECONDS)) }
                        catch (interrupted: InterruptedException) { throw java.io.InterruptedIOException() }
                    }
                    return delegate.open(spec)
                }
            }
        }
        try {
            withContext(Dispatchers.Main) {
                runtime=Media3CoreRuntime(context,dataSourceFactory=factory,gain=0.05f)
                core=PlaybackCore(parent,ContentResolver { PlayableResource((if(it==a) first else second).toURI().toString()) },runtime)
            }
            core.command(PlaybackCommand.ReplaceQueue(listOf(a,b))); core.command(PlaybackCommand.Play)
            withTimeout(2500) { core.state.first { it.selected==b && it.phase in setOf(Phase.Preparing,Phase.Buffering,Phase.Starting) } }
            assertFalse(withContext(Dispatchers.Main) { runtime.player.isPlaying })
            core.command(PlaybackCommand.Pause)
            gate.countDown()
            core.await { it.selected==b && it.phase==Phase.ReadyPaused }
            delay(200)
            assertFalse(withContext(Dispatchers.Main) { runtime.player.isPlaying })
        } finally { gate.countDown(); withContext(Dispatchers.Main) { core.close(); parent.cancel() }; root.deleteRecursively() }
    }

}
