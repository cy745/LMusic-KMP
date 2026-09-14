package com.lalilu.lplayer.extensions

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lplayer.playback.PlatformQueueBridge
import com.lalilu.lplayer.playback.restorePausedQueueIfOwned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(UnstableApi::class)
class QueueDeletionRecoveryDeviceTest {
    private val a = LAudio(id = "a", mediaSourceName = "sandbox")
    private val b = LAudio(id = "b", mediaSourceName = "local")
    private val c = LAudio(id = "c", mediaSourceName = "local")

    @Test fun explicitClearOfEmptyQueueCannotBeUndoneByDeletionRecovery() = withPlayer { player, bridge ->
        bridge.queue.update { replaceAll(listOf(a), 0) }
        val original = bridge.queue.stateSnapshot()
        bridge.queue.update { removeAll(setOf(a.mediaKey)) }
        val expected = bridge.queue.stateSnapshot()
        player.clearMediaItems()
        bridge.queue.update { clear() }
        assertFalse(restorePausedQueueIfOwned(bridge, player, expected, original, 12345, false))
        assertEquals(0, player.mediaItemCount)
    }

    @Test fun restoresEmptyQueueWithDuplicateSelectionPausedAtSavedPosition() = withPlayer { player, bridge ->
        bridge.queue.update { replaceAll(listOf(a, a), 1) }
        val original = bridge.queue.stateSnapshot()
        bridge.queue.update { removeAll(setOf(a.mediaKey)) }
        player.clearMediaItems()
        player.play()
        assertTrue(restorePausedQueueIfOwned(bridge, player, bridge.queue.stateSnapshot(), original, 12345, false))
        assertEquals(2, player.mediaItemCount)
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(a.playbackId, player.currentMediaItem?.mediaId)
        assertEquals(12345L, player.currentPosition)
        assertFalse(player.playWhenReady)
        assertEquals(1, bridge.queue.stateSnapshot().index)
    }

    @Test fun newerNativeSelectionCannotBeOverwrittenBeforeItsMirrorArrives() = withPlayer { player, bridge ->
        bridge.queue.update { replaceAll(listOf(a, b, c), 0) }
        val original = bridge.queue.stateSnapshot()
        bridge.queue.update { removeAll(setOf(a.mediaKey)) }
        val expected = bridge.queue.stateSnapshot()
        player.setMediaItems(listOf(b, c).map { it.toMediaItem() }, 1, 7000)
        player.play()
        assertFalse(restorePausedQueueIfOwned(bridge, player, expected, original, 12345, false) {
            error("Must not start restoration")
        })
        assertEquals(c.playbackId, player.currentMediaItem?.mediaId)
        assertEquals(7000L, player.currentPosition)
        assertTrue(player.playWhenReady)
    }

    @Test fun userReorderingAndMovingBackStillCancelsRestoration() = withPlayer { player, bridge ->
        bridge.queue.update { replaceAll(listOf(a, b, c), 0) }
        val original = bridge.queue.stateSnapshot()
        bridge.queue.update { removeAll(setOf(a.mediaKey)) }
        val expected = bridge.queue.stateSnapshot()
        bridge.queue.update { move(0, 1) }
        bridge.queue.update { move(1, 0) }
        player.setMediaItems(listOf(b, c).map { it.toMediaItem() }, 0, 7000)
        player.play()
        assertFalse(restorePausedQueueIfOwned(bridge, player, expected, original, 12345, false))
        assertEquals(2, player.mediaItemCount)
        assertEquals(7000L, player.currentPosition)
        assertTrue(player.playWhenReady)
    }

    private fun withPlayer(test: suspend (QueueControlPlayer, PlatformQueueBridge) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        lateinit var player: QueueControlPlayer
        lateinit var job: Job
        instrumentation.runOnMainSync {
            player = QueueControlPlayer(ExoPlayer.Builder(instrumentation.targetContext).build(), {}, {}, {}, {})
            val bridge = PlatformQueueBridge(applyToPlatform = {}, readPlatformAfterApply = {})
            job = CoroutineScope(Dispatchers.Main).launch {
                try { test(player, bridge) } catch (error: Throwable) { failure = error }
                finally { done.countDown() }
            }
        }
        try {
            assertTrue(done.await(10, TimeUnit.SECONDS))
            failure?.let { throw it }
        } finally {
            instrumentation.runOnMainSync { job.cancel(); player.release() }
        }
    }
}
