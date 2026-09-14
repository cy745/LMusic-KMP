package com.lalilu.lplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.domain.model.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real decoded silence reaches the engine's natural end; no simulated listener callbacks. */
@androidx.annotation.OptIn(UnstableApi::class)
class QueueAutomaticTransitionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try { block() } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    private fun withAudioPlayer(block: (QueueControlPlayer, List<MediaItem>) -> Unit) {
        val fixture = File.createTempFile("queue-natural-end-", ".wav", instrumentation.targetContext.cacheDir)
        // 400 ms, mono PCM16, 8 kHz. Only this test-owned temporary file is deleted afterward.
        val pcmSize = 6400
        val data = ByteBuffer.allocate(44 + pcmSize).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + pcmSize).put("WAVEfmt ".toByteArray())
        data.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
        data.putShort(2).putShort(16).put("data".toByteArray()).putInt(pcmSize)
        fixture.writeBytes(data.array())
        lateinit var player: QueueControlPlayer
        var created = false
        try {
            onMain {
                player = QueueControlPlayer(ExoPlayer.Builder(instrumentation.targetContext).build(), {}, {}, {}, {})
                created = true
            }
            val items = (0..3).map { id ->
                MediaItem.Builder().setMediaId(MediaKey("automatic-test", "$id").stableKey)
                    .setUri(fixture.toURI().toString()).build()
            }
            block(player, items)
        } finally {
            if (created) onMain { player.release() }
            fixture.delete()
        }
    }

    private fun awaitState(player: QueueControlPlayer, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            var reached = false
            onMain {
                player.playerError?.let { throw AssertionError("Native playback failed", it) }
                reached = predicate()
                if (reached) player.pause()
            }
            if (reached) return
            Thread.sleep(20)
        }
        throw AssertionError("Native playback did not reach expected state within 10 seconds")
    }

    @Test
    fun sequentialStopsAtLastSong() = withAudioPlayer { player, items ->
        onMain {
            player.setMediaItems(items, 3, 0)
            player.playMode = PlayMode.Sequential
            player.prepare()
            player.play()
        }
        awaitState(player) { player.playbackState == Player.STATE_ENDED }
        onMain { assertEquals(items[3].mediaId, player.currentMediaItem?.mediaId) }
    }

    @Test
    fun listLoopContinuesAtFirstSong() = withAudioPlayer { player, items ->
        onMain {
            player.setMediaItems(items, 3, 0)
            player.playMode = PlayMode.ListRecycle
            player.prepare()
            player.play()
        }
        awaitState(player) { player.currentMediaItem?.mediaId == items[0].mediaId && player.isPlaying }
    }

    @Test
    fun sequentialEndHonorsExplicitNextRequest() = withAudioPlayer { player, items ->
        onMain {
            player.setMediaItems(items, 3, 0)
            player.playMode = PlayMode.Sequential
            player.requestPlayNext(items[1].mediaId)
            player.prepare()
            player.play()
        }
        awaitState(player) { player.currentMediaItem?.mediaId == items[1].mediaId && player.isPlaying }
    }

    @Test
    fun shuffleAutomaticExplicitNextPreservesPreviousSong() = withAudioPlayer { player, items ->
        onMain {
            player.setMediaItems(items, 2, 0)
            player.playMode = PlayMode.Shuffle
            player.requestPlayNext(items[0].mediaId)
            player.prepare()
            player.play()
        }
        awaitState(player) { player.currentMediaItem?.mediaId == items[0].mediaId && player.isPlaying }
        onMain {
            assertEquals(1, player.currentMediaItemIndex)
            assertEquals(items[2].mediaId, player.getMediaItemAt(2).mediaId)
            assertTrue((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                == items.map { it.mediaId }.toSet())
        }
    }
}
