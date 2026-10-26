package com.lalilu.playback.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeTransitionTest {
    @Test fun controlRampIsMonotonicAndEndsAtTheRequestedGain() = runTest {
        val values = mutableListOf<Pair<Long, Float>>()
        val job = launch { rampVolume(0f, 0.6f, VolumeTransition.UserControl, { testScheduler.currentTime }) { values += testScheduler.currentTime to it } }
        runCurrent(); advanceTimeBy(100); runCurrent()
        assertTrue(values.last().second in 0.1f..0.5f)
        advanceUntilIdle(); job.join()
        assertEquals(220L, values.last().first)
        assertEquals(0.6f, values.last().second)
        assertTrue(values.zipWithNext().all { (a,b) -> b.second >= a.second })
    }

    @Test fun reversingFromCurrentGainCancelsTheOldPauseCompletion() = runTest {
        var gain = 1f
        var paused = false
        val down = launch {
            rampVolume(gain, 0f, VolumeTransition.UserControl, { testScheduler.currentTime }) { gain = it }
            paused = true
        }
        runCurrent(); advanceTimeBy(72); runCurrent()
        val interrupted = gain
        assertTrue(interrupted in 0.1f..0.99f)
        down.cancelAndJoin()
        val values = mutableListOf<Float>()
        launch { rampVolume(gain, 1f, VolumeTransition.UserControl, { testScheduler.currentTime }) { gain = it; values += it } }
        advanceUntilIdle()
        assertEquals(interrupted, values.first())
        assertEquals(1f, gain)
        assertFalse(paused)
        assertTrue(values.zipWithNext().all { (a,b) -> b >= a })
    }

    @Test fun manualSelectionIsShortAndForcedInterruptionIsImmediate() = runTest {
        rampVolume(1f, 0f, VolumeTransition.TrackChange, { testScheduler.currentTime }) {}
        assertEquals(60L, testScheduler.currentTime)
        var gain = 1f
        rampVolume(gain, 0f, VolumeTransition.Immediate, { testScheduler.currentTime }) { gain = it }
        assertEquals(0f, gain)
        assertEquals(60L, testScheduler.currentTime)
    }
}
