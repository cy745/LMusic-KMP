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

@OptIn(ExperimentalTestApi::class)
class PlaybackPositionRecompositionTest {
    @Test
    fun injectedBufferingFreezesPositionWithoutInvalidatingTheParent() = runComposeUiTest {
        val owner = TestLifecycleOwner()
        val playing = mutableStateOf(true)
        val key = mutableStateOf<Any?>("a")
        val frozen = mutableStateOf(false)
        val source = PositionSource(100)
        val observation = ClockObservation()
        mainClock.autoAdvance = false
        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        setContent { ClockHost(owner, playing, key, source, observation, frozen) }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val clock = runOnIdle { observation.clock!! }
        val before = runOnIdle { observation.commits }

        runOnUiThread { frozen.value = true; source.position = 1_200 }
        mainClock.advanceTimeBy(250)
        waitForIdle()
        assertEquals(100, runOnIdle { clock.position.longValue })
        runOnUiThread { frozen.value = false }
        mainClock.advanceTimeBy(250)
        waitForIdle()
        assertEquals(1_200, runOnIdle { clock.position.longValue })
        assertSame(clock, runOnIdle { observation.clock })
        assertEquals(before, runOnIdle { observation.commits })
    }

    @Test
    fun playPauseSeekAndFrameTicksKeepTheParentScopeAndClockIdentity() = runComposeUiTest {
        val owner = TestLifecycleOwner()
        val playing = mutableStateOf(true)
        val key = mutableStateOf<Any?>("a")
        val source = PositionSource(100)
        val observation = ClockObservation()
        mainClock.autoAdvance = false
        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        setContent { ClockHost(owner, playing, key, source, observation) }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val clock = runOnIdle { observation.clock!! }
        val before = runOnIdle { observation.commits }

        runOnUiThread { source.position = 1_200 }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        assertEquals(1_200, runOnIdle { clock.position.longValue })

        runOnUiThread { playing.value = false }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        // A paused seek/history restore can finish after the pause effect has started.
        runOnUiThread { source.position = 8_100 }
        mainClock.advanceTimeBy(250)
        waitForIdle()
        assertEquals(8_100, runOnIdle { clock.position.longValue })

        runOnUiThread { source.position = 10_000; playing.value = true }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        assertEquals(10_000, runOnIdle { clock.position.longValue })
        assertSame(clock, runOnIdle { observation.clock })
        assertEquals(before, runOnIdle { observation.commits },
            "Playback state and position frames must not invalidate the parent scope")
    }

    @Test
    fun songSwitchAndLifecycleResumeSynchronizeWithoutReplacingTheClock() = runComposeUiTest {
        val owner = TestLifecycleOwner()
        val playing = mutableStateOf(false)
        val key = mutableStateOf<Any?>("a")
        val source = PositionSource(400)
        val observation = ClockObservation()
        mainClock.autoAdvance = false
        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        setContent { ClockHost(owner, playing, key, source, observation) }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val clock = runOnIdle { observation.clock!! }
        val before = runOnIdle { observation.commits }

        runOnUiThread { source.position = 0; key.value = "b" }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        assertEquals("b", runOnIdle { clock.sampledPlaybackKey })
        assertEquals(0, runOnIdle { clock.position.longValue })
        assertSame(clock, runOnIdle { observation.clock })
        assertEquals(before + 1, runOnIdle { observation.commits })

        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        val readsBeforePause = runOnIdle { source.reads }
        runOnUiThread { source.position = 9_000 }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        assertEquals(readsBeforePause, runOnIdle { source.reads }, "Paused lifecycle must cancel polling")
        assertEquals(0, runOnIdle { clock.position.longValue })

        runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        repeat(2) { mainClock.advanceTimeByFrame(); waitForIdle() }
        assertEquals(9_000, runOnIdle { clock.position.longValue })
        assertEquals("b", runOnIdle { clock.sampledPlaybackKey })
        assertSame(clock, runOnIdle { observation.clock })
        assertEquals(before + 1, runOnIdle { observation.commits })
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)
}

private class PositionSource(var position: Long) {
    var reads = 0
    fun read(): Long { reads++; return position }
}

private class ClockObservation {
    var commits = 0
    var clock: PlaybackPositionState? = null
}

@Composable
private fun ClockHost(owner: LifecycleOwner, playing: MutableState<Boolean>, key: MutableState<Any?>,
    source: PositionSource, observation: ClockObservation, frozen: MutableState<Boolean>? = null) {
    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        val clock = rememberPlaybackPositionState(playing, key.value, source::read,
            frozen = { frozen?.value ?: false })
        SideEffect { observation.commits++; observation.clock = clock }
    }
}
