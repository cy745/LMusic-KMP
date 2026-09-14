package com.lalilu.lplayer.extensions

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lplayer.playback.PlaybackFailureTraversal
import com.lalilu.lplayer.playback.PreparationControl
import com.lalilu.lplayer.playback.runPlaybackFailureRecovery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real native controls, with a deliberately suspended failing repository operation. */
@androidx.annotation.OptIn(UnstableApi::class)
class QueueRecoveryControlDeviceTest {
    @Test fun currentLookupFailurePauses() = checkRecovery(null)
    @Test fun pauseThenResumeCannotBePausedByOldLookup() = checkRecovery("pause")
    @Test fun stopThenResumeCannotBePausedByOldLookup() = checkRecovery("stop")

    private fun checkRecovery(takeover: String?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val traversal = PlaybackFailureTraversal()
        val preparation = PreparationControl()
        val entered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val lookup = CompletableDeferred<Unit>()
        var pauses = 0
        var failure: Throwable? = null
        lateinit var player: QueueControlPlayer
        lateinit var job: Job
        instrumentation.runOnMainSync {
            player = QueueControlPlayer(ExoPlayer.Builder(instrumentation.targetContext).build(),
                { traversal.begin(it) }, { traversal.cancel() },
                { pauses++; preparation.pause() }, { preparation.stop() })
            player.play()
            val ticket = traversal.generation
            job = CoroutineScope(Dispatchers.Main).launch {
                try {
                    runPlaybackFailureRecovery({ ticket == traversal.generation }, {}, { player.pause() }) {
                        entered.countDown()
                        lookup.await()
                    }
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    finished.countDown()
                }
            }
        }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                if (takeover == "pause") { player.pause(); player.play() }
                if (takeover == "stop") { player.stop(); player.play() }
                lookup.completeExceptionally(IllegalStateException("test lookup unavailable"))
            }
            assertTrue(finished.await(10, TimeUnit.SECONDS))
            failure?.let { throw it }
            instrumentation.runOnMainSync {
                assertEquals(takeover != null, player.playWhenReady)
                assertEquals(if (takeover == "stop") 0 else 1, pauses)
            }
        } finally {
            instrumentation.runOnMainSync { job.cancel(); player.release() }
        }
    }
}
