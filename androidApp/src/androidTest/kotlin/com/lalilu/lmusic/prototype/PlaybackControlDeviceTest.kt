package com.lalilu.lmusic.prototype

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lplayer.core.CoreSessionPlayer
import com.lalilu.lplayer.core.Media3CoreRuntime
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PlaybackControlDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun waitFor(label: String, predicate: () -> Boolean) {
        try { withTimeout(10000) { while (!withContext(Dispatchers.Main) { predicate() }) delay(10) } }
        catch (failure: TimeoutCancellationException) { throw AssertionError(label, failure) }
    }
    private fun scenario(block: suspend (PlaybackCore, Media3CoreRuntime, CoreSessionPlayer) -> Unit) = runBlocking {
        val directory = File(context.cacheDir, "control-${System.nanoTime()}")
        val uri = PrototypeAudio.write(directory, "control", durationMs = 30000).toURI().toString()
        val parent = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var runtime: Media3CoreRuntime
        lateinit var core: PlaybackCore
        var ownedPlayer: CoreSessionPlayer? = null
        lateinit var sessionPlayer: CoreSessionPlayer
        try {
            withContext(Dispatchers.Main) {
                runtime = Media3CoreRuntime(context, gain = 0.25f)
                core = PlaybackCore(parent, ContentResolver { PlayableResource(uri) }, runtime)
                sessionPlayer = CoreSessionPlayer(core, runtime, parent, mutableMapOf())
                ownedPlayer = sessionPlayer
                sessionPlayer.setMediaItems(listOf("A", "B").map { MediaItem.Builder().setMediaId("7:control$it").setUri(uri).build() })
            }
            withTimeout(10000) { core.state.first { it.phase == Phase.ReadyPaused } }
            block(core, runtime, sessionPlayer)
        } finally {
            withContext(Dispatchers.Main) { ownedPlayer?.release(); parent.cancel() }
            directory.deleteRecursively()
        }
    }

    @Test fun sessionPauseAndResumeHaveAudibleGainRamps() = scenario { core, runtime, player ->
        val gains = mutableListOf<Pair<Long, Float>>()
        val listener = object : Player.Listener {
            override fun onVolumeChanged(volume: Float) { gains += SystemClock.elapsedRealtime() to volume }
        }
        withContext(Dispatchers.Main) { runtime.player.addListener(listener); player.play() }
        waitFor("initial fade completes") { runtime.player.isPlaying && runtime.player.volume >= 0.249f }
        withContext(Dispatchers.Main) { gains.clear(); player.pause() }
        waitFor("native pause follows fade") { !runtime.player.playWhenReady && runtime.player.volume == 0f }
        withContext(Dispatchers.Main) {
            assertEquals(PlayIntent.Pause, core.state.value.intent)
            assertTrue("Pause should have a perceptible short fade: $gains", gains.last().first - gains.first().first >= 150)
            assertTrue(gains.zipWithNext().all { (a,b) -> b.second <= a.second })
            println("CONTROL_FADE pause samples=${gains.size} durationMs=${gains.last().first - gains.first().first}")
            gains.clear(); player.play()
        }
        waitFor("resume fade completes") { runtime.player.isPlaying && runtime.player.volume >= 0.249f }
        withContext(Dispatchers.Main) {
            assertTrue("Resume should have a perceptible short fade: $gains", gains.last().first - gains.first().first >= 150)
            assertTrue(gains.zipWithNext().all { (a,b) -> b.second >= a.second })
            println("CONTROL_FADE resume samples=${gains.size} durationMs=${gains.last().first - gains.first().first}")
        }
    }
    @Test fun rapidCommandsAndSelectionCannotBeOverriddenByAnOldFade() = scenario { core, runtime, player ->
        withContext(Dispatchers.Main) { player.play() }
        waitFor("playing") { runtime.player.isPlaying && runtime.player.volume >= 0.249f }
        withContext(Dispatchers.Main) { player.pause() }
        delay(72)
        withContext(Dispatchers.Main) {
            assertTrue(runtime.player.volume in 0.01f..0.24f)
            player.play()
        }
        waitFor("reversed fade restores gain") { runtime.player.volume >= 0.249f }
        delay(300)
        withContext(Dispatchers.Main) { assertTrue(runtime.player.isPlaying) }
        repeat(10) {
            withContext(Dispatchers.Main) { player.pause() }
            delay(15)
            withContext(Dispatchers.Main) { player.play() }
        }
        withContext(Dispatchers.Main) { player.pause() }
        waitFor("last pause wins") { !runtime.player.playWhenReady && runtime.player.volume == 0f }
        withContext(Dispatchers.Main) { player.play() }
        waitFor("playing again") { runtime.player.volume >= 0.249f }
        withContext(Dispatchers.Main) {
            player.pause()
            core.dispatch(PlaybackCommand.Select(TrackKey("control", "B")))
        }
        withTimeout(10000) { core.state.first { it.selected?.id == "B" && it.phase == Phase.ReadyPaused } }
        delay(300)
        withContext(Dispatchers.Main) {
            assertEquals(PlayIntent.Pause, core.state.value.intent)
            assertFalse(runtime.player.playWhenReady)
            assertEquals(0f, runtime.player.volume)
        }
    }

    @Test fun transientFocusResumesOnlyWhenUserHasNotPausedAndPermanentLossStaysPaused() = scenario { core, runtime, player ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(android.content.Intent(context, com.lalilu.lmusic.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val manager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        fun request(gain: Int) = android.media.AudioFocusRequest.Builder(gain)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener({}, android.os.Handler(android.os.Looper.getMainLooper())).build()
        val temporary = request(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        val permanent = request(android.media.AudioManager.AUDIOFOCUS_GAIN)
        var focusPauseNotifications = 0
        var resumedAfterUserPause = false
        var userPausedDuringInterruption = false
        try {
            withContext(Dispatchers.Main) {
                runtime.player.setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                player.play()
            }
            waitFor("focused playback") { runtime.player.isPlaying }
            withContext(Dispatchers.Main) {
                runtime.player.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) {
                        if (!value && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) focusPauseNotifications++
                        println("FOCUS_READY value=$value reason=$reason")
                    }
                    override fun onIsPlayingChanged(value: Boolean) {
                        if (value && userPausedDuringInterruption) resumedAfterUserPause = true
                    }
                    override fun onPlaybackSuppressionReasonChanged(reason: Int) { println("FOCUS_SUPPRESSION reason=$reason") }
                })
            }
            assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(temporary))
            delay(300)
            withContext(Dispatchers.Main) { println("FOCUS_OBS phase=${core.state.value.phase} intent=${core.state.value.intent} nativePlaying=${runtime.player.isPlaying} nativeIntent=${runtime.player.playWhenReady} suppression=${runtime.player.playbackSuppressionReason}") }
            waitFor("transient suppression") { core.state.value.phase == Phase.Suppressed && !runtime.player.isPlaying }
            manager.abandonAudioFocusRequest(temporary)
            waitFor("allowed system resume") { core.state.value.phase == Phase.Playing && runtime.player.isPlaying }
            assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(temporary))
            waitFor("second suppression") { core.state.value.phase == Phase.Suppressed }
            withContext(Dispatchers.Main) { userPausedDuringInterruption = true; player.pause() }
            withTimeout(5000) { core.state.first { it.intent == PlayIntent.Pause } }
            withContext(Dispatchers.Main) { assertFalse("Suppressed pause must revoke native auto-resume immediately", runtime.player.playWhenReady) }
            manager.abandonAudioFocusRequest(temporary)
            delay(400)
            withContext(Dispatchers.Main) {
                assertEquals(PlayIntent.Pause, core.state.value.intent)
                assertFalse(runtime.player.playWhenReady)
                assertFalse("Focus restoration must never briefly resume after user pause", resumedAfterUserPause)
                userPausedDuringInterruption = false
                player.play()
            }
            waitFor("explicit resume") { runtime.player.isPlaying }
            assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(temporary))
            waitFor("suppression before selection") { core.state.value.phase == Phase.Suppressed }
            withContext(Dispatchers.Main) { player.seekToNextMediaItem() }
            withTimeout(10000) { core.state.first { it.selected?.id == "B" && it.phase == Phase.ReadyPaused } }
            manager.abandonAudioFocusRequest(temporary)
            delay(300)
            withContext(Dispatchers.Main) {
                assertEquals(PlayIntent.Pause, core.state.value.intent)
                assertFalse(runtime.player.playWhenReady)
                player.play()
            }
            waitFor("explicit resume after selection") { runtime.player.isPlaying }
            assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(permanent))
            waitFor("permanent loss cancels intent") { core.state.value.intent == PlayIntent.Pause && !runtime.player.playWhenReady && runtime.player.volume == 0f }
            manager.abandonAudioFocusRequest(permanent)
            delay(300)
            withContext(Dispatchers.Main) {
                assertFalse(runtime.player.playWhenReady)
                assertTrue("System pause must not loop through repeated focus callbacks: $focusPauseNotifications", focusPauseNotifications <= 2)
            }
        } finally {
            manager.abandonAudioFocusRequest(temporary)
            manager.abandonAudioFocusRequest(permanent)
            withContext(Dispatchers.Main) { activity.finish() }
        }
    }

}
