package com.lalilu.lplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lplayer.playback.PlaybackDirection
import com.lalilu.lplayer.playback.PlaybackFailureTraversal
import com.lalilu.lplayer.playback.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Integrates the production failure budget with native errors and real shuffle timeline changes. */
@androidx.annotation.OptIn(UnstableApi::class)
class QueueFailureTraversalDeviceTest {
    @Test fun allMissingShuffleSongsStopInForwardDirection() = runFailures(PlaybackDirection.Forward)
    @Test fun allMissingShuffleSongsStopInBackwardDirection() = runFailures(PlaybackDirection.Backward)

    private fun runFailures(direction: PlaybackDirection) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val traversal = PlaybackFailureTraversal()
        val ended = CountDownLatch(1)
        val attempts = mutableListOf<String>()
        var failure: Throwable? = null
        lateinit var player: QueueControlPlayer
        instrumentation.runOnMainSync {
            val engine = ExoPlayer.Builder(instrumentation.targetContext).build()
            player = QueueControlPlayer(engine, { traversal.begin(it) }, { traversal.cancel() }, {}, {})
            engine.addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    traversal.observeQueue((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
                }

                override fun onPlayerError(error: PlaybackException) {
                    try {
                        attempts += requireNotNull(player.currentMediaItem).mediaId
                        check(attempts.size <= 4) { "Repeated a failed song: $attempts" }
                        val next = traversal.next(traversal.generation, player.mediaItemCount,
                            player.currentMediaItemIndex, PlaybackMode.SHUFFLE) { true }
                        if (next == null) {
                            player.pause()
                            ended.countDown()
                        } else {
                            player.seekTo(next, 0)
                            player.prepare()
                        }
                    } catch (error: Throwable) {
                        failure = error
                        player.pause()
                        ended.countDown()
                    }
                }
            })
            val items = (0..3).map {
                MediaItem.Builder().setMediaId(MediaKey("failed-native-test", "$it").stableKey)
                    .setUri("file:///nonexistent-lmusic-native-fixture-$it.wav").build()
            }
            player.setMediaItems(items, 2, 0)
            player.playMode = PlayMode.Shuffle
            traversal.begin(direction)
            player.prepare()
            player.play()
        }
        try {
            assertTrue("Native errors must stop within 10 seconds", ended.await(10, TimeUnit.SECONDS))
            failure?.let { throw it }
            assertEquals(4, attempts.size)
            assertEquals(4, attempts.toSet().size)
            instrumentation.runOnMainSync {
                assertFalse(player.playWhenReady)
            }
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }
}
