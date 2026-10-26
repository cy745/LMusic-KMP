package com.lalilu.lmusic.prototype

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lplayer.core.CoreSessionPlayer
import com.lalilu.lplayer.core.Media3CoreRuntime
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in wall-clock soak. Uses real native decoding/output with test-owned local audio, muted gain. */
class PlaybackSoakDeviceTest {
    @Test fun sustainedNativePlaybackKeepsWindowBoundedAndReleasesThreads() = runBlocking {
        val seconds = InstrumentationRegistry.getArguments().getString("soakSeconds")?.toLongOrNull()
        assumeTrue("Set soakSeconds (60..3600) for the sustained native run", seconds != null)
        require(seconds!! in 60..3600)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "soak-${System.nanoTime()}")
        val file = PrototypeAudio.write(directory, "soak", durationMs=3000)
        fun descriptors() = File("/proc/self/fd").list()?.size ?: error("Cannot count descriptors")
        // BackgroundExecutor is a process-wide Media3 singleton, not owned by this player.
        fun nativeThreads() = Thread.getAllStackTraces().keys.count {
            it.isAlive && it.name.startsWith("ExoPlayer") && it.name != "ExoPlayer:BackgroundExecutor"
        }
        val threadsBefore = nativeThreads()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val items = mutableMapOf<TrackKey, MediaItem>()
        lateinit var runtime: Media3CoreRuntime
        lateinit var core: PlaybackCore
        lateinit var player: CoreSessionPlayer
        var owned: CoreSessionPlayer? = null
        var natural = 0
        var selections = 0
        var operations = 0
        var maxWindow = 0
        suspend fun waitFor(label: String, test: () -> Boolean) {
            try { withTimeout(10000) { while (!withContext(Dispatchers.Main) { test() }) delay(20) } }
            catch (failure: TimeoutCancellationException) { throw AssertionError(label, failure) }
        }
        try {
            withContext(Dispatchers.Main) {
                runtime = Media3CoreRuntime(context, gain=0f)
                core = PlaybackCore(scope, ContentResolver { PlayableResource(file.toURI().toString()) }, runtime)
                player = CoreSessionPlayer(core, runtime, scope, items)
                owned = player
                core.state.map { it.ticket to it.naturalTransition }.distinctUntilChanged().onEach { (ticket, auto) ->
                    if (ticket != null) { selections++; if (auto) natural++ }
                }.launchIn(scope)
                player.setMediaItems((0 until 5000).map { MediaItem.Builder().setMediaId(MediaKey("soak", "$it").stableKey).build() })
            }
            waitFor("prepared") { core.state.value.phase == Phase.ReadyPaused }
            withContext(Dispatchers.Main) { player.play() }
            waitFor("native playback") { runtime.player.isPlaying && runtime.player.currentPosition > 0 }
            val baselineFd = descriptors()
            var peakFd = baselineFd
            val started = SystemClock.elapsedRealtime()
            var nextOperation = started + 5000
            var nextReport = started + 60000
            while (SystemClock.elapsedRealtime() - started < seconds * 1000) {
                delay(200)
                withContext(Dispatchers.Main) {
                    assertNull(core.state.value.error)
                    maxWindow = maxOf(maxWindow, runtime.player.mediaItemCount)
                    assertTrue("Native queue stays bounded", runtime.player.mediaItemCount <= 2)
                    assertEquals(5000, core.state.value.queue.size)
                }
                peakFd = maxOf(peakFd, descriptors())
                val now = SystemClock.elapsedRealtime()
                if (now >= nextOperation) {
                    operations++
                    when (operations % 3) {
                        0 -> {
                            withContext(Dispatchers.Main) { repeat(3) { player.seekToNextMediaItem() } }
                            waitFor("rapid navigation settles") { runtime.player.isPlaying && core.state.value.phase == Phase.Playing }
                        }
                        1 -> {
                            withContext(Dispatchers.Main) { player.pause() }
                            waitFor("pause settles") { !runtime.player.playWhenReady }
                            assertEquals(PlayIntent.Pause, core.state.value.intent)
                            withContext(Dispatchers.Main) { player.play() }
                            waitFor("resume settles") { runtime.player.isPlaying }
                        }
                        2 -> {
                            withContext(Dispatchers.Main) { player.seekTo(500) }
                            waitFor("seek settles") { runtime.player.isPlaying && core.state.value.pendingSeekId == null }
                        }
                    }
                    nextOperation = SystemClock.elapsedRealtime() + 5000
                }
                if (now >= nextReport) {
                    println("PLAYBACK_SOAK elapsedMs=${now-started} operations=$operations natural=$natural fd=${descriptors()} peakFd=$peakFd window=$maxWindow")
                    nextReport = now + 60000
                }
            }
            assertTrue("Natural advancement must actually occur", natural >= seconds / 10)
            assertTrue("Descriptor use must not grow with each transition", peakFd <= baselineFd + 16)
            val finalFd = descriptors()
            withContext(Dispatchers.Main) { player.release(); owned = null; scope.cancel() }
            Thread.getAllStackTraces().filter { it.key.isAlive && it.key.name.startsWith("ExoPlayer") }.forEach { (thread, stack) ->
                println("SOAK_RELEASE_THREAD ${thread.name} ${stack.joinToString()}")
            }
            waitFor("native playback threads released") { nativeThreads() <= threadsBefore }
            assertTrue(items.isEmpty())
            println("PLAYBACK_SOAK_OK elapsedMs=${SystemClock.elapsedRealtime()-started} queue=5000 operations=$operations selections=$selections natural=$natural window=$maxWindow fdBaseline=$baselineFd fdPeak=$peakFd fdFinal=$finalFd fdReleased=${descriptors()} threadsBefore=$threadsBefore threadsAfter=${nativeThreads()}")
        } finally {
            withContext(Dispatchers.Main) { owned?.release(); scope.cancel() }
            directory.deleteRecursively()
        }
    }
}
