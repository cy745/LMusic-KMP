package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PlaybackTimelineRecompositionTest {
    @Test
    fun frameTicksFollowTheEngineWithoutInvalidatingTheParent() = runComposeUiTest {
        val owner = TestLifecycleOwner()
        val playing = mutableStateOf(true)
        val key = mutableStateOf<Any?>("a")
        val metadataDuration = mutableStateOf(180_000L)
        val source = PositionSource(0L)
        val observation = TimelineObservation()
        mainClock.autoAdvance = false
        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        setContent { TimelineHost(owner, playing, key, metadataDuration, source, observation) }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }

        val timeline = runOnIdle { observation.timeline!! }
        val before = runOnIdle { observation.commits }

        repeat(30) {
            runOnUiThread { source.position += 16L }
            mainClock.advanceTimeByFrame()
            waitForIdle()
        }

        assertEquals(480L, runOnIdle { timeline.positionMs })
        assertEquals(before, runOnIdle { observation.commits },
            "时间轴的逐帧更新不得让父级重组")
    }

    @Test
    fun songSwitchKeepsTheTimelineIdentityAndSwitchesDurationAtomically() = runComposeUiTest {
        val owner = TestLifecycleOwner()
        val playing = mutableStateOf(true)
        val key = mutableStateOf<Any?>("a")
        val metadataDuration = mutableStateOf(180_000L)
        val source = PositionSource(0L)
        val observation = TimelineObservation()
        mainClock.autoAdvance = false
        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        setContent { TimelineHost(owner, playing, key, metadataDuration, source, observation) }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }

        val timeline = runOnIdle { observation.timeline!! }
        val before = runOnIdle { observation.commits }

        runOnUiThread {
            key.value = "b"
            metadataDuration.value = 200_000L
        }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }

        assertSame(timeline, runOnIdle { observation.timeline })
        assertEquals("b", runOnIdle { timeline.playbackKey })
        assertEquals(200_000L, runOnIdle { timeline.durationMs })
        assertEquals(before + 1, runOnIdle { observation.commits })
    }

    @Test
    fun pausedTimelineStillAdvancesTheTransitionOnEveryFrame() = runComposeUiTest {
        val owner = TestLifecycleOwner()
        val playing = mutableStateOf(true)
        val key = mutableStateOf<Any?>("a")
        val metadataDuration = mutableStateOf(180_000L)
        val source = PositionSource(0L)
        val observation = TimelineObservation()
        mainClock.autoAdvance = false
        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        setContent { TimelineHost(owner, playing, key, metadataDuration, source, observation) }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val timeline = runOnIdle { observation.timeline!! }

        runOnUiThread { playing.value = false }
        repeat(3) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val settled = runOnIdle { timeline.positionMs }

        // 暂停期间引擎位置被外部改变（恢复历史进度、seek 落地等）
        runOnUiThread { source.position = settled + 30_000L }
        var frames = 0
        while (runOnIdle { timeline.positionMs } == settled && frames < 40) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
            frames++
        }
        val started = runOnIdle { timeline.positionMs }
        assertTrue(started != settled, "暂停时也必须察觉引擎位置的变化")

        // 过渡一旦开始就必须逐帧推进：若只在采样时推进，这里会原地不动。
        repeat(3) { mainClock.advanceTimeByFrame(); waitForIdle() }
        assertTrue(
            runOnIdle { timeline.positionMs } != started,
            "暂停时的缓动必须按帧推进，不能在采样间隔里停住",
        )
    }

    @Test
    fun pausedLifecycleStopsPollingAndResyncsOnResume() = runComposeUiTest {
        val owner = TestLifecycleOwner()
        val playing = mutableStateOf(true)
        val key = mutableStateOf<Any?>("a")
        val metadataDuration = mutableStateOf(180_000L)
        val source = PositionSource(0L)
        val observation = TimelineObservation()
        mainClock.autoAdvance = false
        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        setContent { TimelineHost(owner, playing, key, metadataDuration, source, observation) }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val timeline = runOnIdle { observation.timeline!! }

        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val readsBeforePause = runOnIdle { source.reads }
        runOnUiThread { source.position = 9_000L }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertEquals(readsBeforePause, runOnIdle { source.reads }, "暂停后必须停止采样")

        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        repeat(60) { mainClock.advanceTimeByFrame(); waitForIdle() }
        assertEquals(9_000L, runOnIdle { timeline.positionMs })
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)
}

private class PositionSource(var position: Long) {
    var reads = 0
    fun read(): Long {
        reads++
        return position
    }
}

private class TimelineObservation {
    var commits = 0
    var timeline: PlaybackTimelineState? = null
}

@Composable
private fun TimelineHost(
    owner: LifecycleOwner,
    playing: MutableState<Boolean>,
    key: MutableState<Any?>,
    metadataDuration: MutableState<Long>,
    source: PositionSource,
    observation: TimelineObservation,
) {
    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        val timeline = rememberPlaybackTimelineState(
            isPlaying = playing.value,
            playbackKey = key.value,
            readPosition = source::read,
            readDuration = { metadataDuration.value },
            readBufferedPosition = { 0L },
        )
        SideEffect {
            observation.commits++
            observation.timeline = timeline
        }
    }
}
