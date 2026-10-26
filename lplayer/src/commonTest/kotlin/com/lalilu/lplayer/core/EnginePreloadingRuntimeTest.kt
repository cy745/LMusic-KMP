package com.lalilu.lplayer.core

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lplayer.playback.*
import com.lalilu.playback.core.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EnginePreloadingRuntimeTest {
    private class Engine : ContinuousPlaybackEngine {
        override val state = MutableStateFlow(PlaybackEngineState(duration = 1000))
        override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null
        override var onNext: (suspend () -> Unit)? = null
        var next: String? = null
        override fun canHandle(mediaData: MediaData, audio: LAudio) = true
        override suspend fun load(mediaData: MediaData, audio: LAudio) { state.value = PlaybackEngineState(duration = 1000) }
        override suspend fun stageNext(mediaData: MediaData, audio: LAudio) { next = audio.id }
        override fun cancelNext(): Boolean { next = null; return false }
        override suspend fun play() { state.value = state.value.copy(isPlaying = true) }
        override suspend fun pause() { state.value = state.value.copy(isPlaying = false) }
        override suspend fun stop() = pause()
        override suspend fun seekTo(positionMs: Long) { state.value = state.value.copy(position = positionMs) }
        override fun currentPosition() = state.value.position
        override suspend fun release() { state.value = PlaybackEngineState.EMPTY }
    }
    @Test fun nativeAdvanceUpdatesTicketAndRejectsLateOldCallbacks() = runTest {
        val engine = Engine()
        val runtime = EngineAudioRuntime(backgroundScope, listOf(engine))
        val events = mutableListOf<RuntimeEvent>()
        runtime.onEvent = { events += it }
        val a = LoadTicket(TrackKey("s", "a"), 1)
        val b = LoadTicket(TrackKey("s", "b"), 2)
        fun resource(id: String) = runtime.resource(MediaData.Url("file:///$id"), LAudio(id = id, mediaSourceName = "s"))
        runtime.silence(a); runtime.prepare(a, resource("a")); runCurrent()
        val old = engine.onEvent!!
        runtime.setPlaying(a, true, VolumeTransition.Immediate); runCurrent()
        val plan = NextPlan(1, a, b)
        runtime.stageNext(plan, resource("b")); runCurrent()
        assertEquals("b", engine.next)
        engine.onNext!!.invoke(); runCurrent()
        assertEquals(plan, events.filterIsInstance<RuntimeEvent.Transitioned>().single().plan)
        runtime.confirmNext(plan)
        events.clear()
        old(PlaybackEngineEvent.Completion); runCurrent()
        assertTrue(events.none { it is RuntimeEvent.Ended })
        engine.onEvent!!.invoke(PlaybackEngineEvent.Completion); runCurrent()
        assertEquals(b, events.filterIsInstance<RuntimeEvent.Ended>().single().ticket)
        runtime.release(); runCurrent()
    }
    @Test fun cancelledSuccessorCannotAdvanceAndPauseRemainsFinalIntent() = runTest {
        val engine = Engine()
        val runtime = EngineAudioRuntime(backgroundScope, listOf(engine))
        val events = mutableListOf<RuntimeEvent>()
        runtime.onEvent = { events += it }
        val a = LoadTicket(TrackKey("s", "a"), 1)
        runtime.silence(a)
        runtime.prepare(a, runtime.resource(MediaData.Url("file:///a"), LAudio("a", mediaSourceName = "s"))); runCurrent()
        runtime.stageNext(NextPlan(1, a, LoadTicket(TrackKey("s", "b"), 2)), runtime.resource(MediaData.Url("file:///b"), LAudio("b", mediaSourceName = "s"))); runCurrent()
        val old = engine.onNext!!
        runtime.cancelNext()
        runtime.setPlaying(a, false, VolumeTransition.Immediate); runCurrent()
        old(); runCurrent()
        assertNull(engine.next)
        assertFalse(engine.state.value.isPlaying)
        assertTrue(events.none { it is RuntimeEvent.Transitioned })
        runtime.release(); runCurrent()
    }
}
