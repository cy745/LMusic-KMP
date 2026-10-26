package com.lalilu.playback.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PreloadingCoreTest {
    private val a = TrackKey("s", "a")
    private val b = TrackKey("s", "b")
    private val c = TrackKey("s", "c")
    private class Runtime : PreloadingAudioRuntime {
        override var onEvent: ((RuntimeEvent) -> Unit)? = null
        var staged: NextPlan? = null
        var silenceCount = 0
        var rejectStage = false
        var unconfirmed: NextPlan? = null
        val plays = mutableListOf<Pair<LoadTicket,Boolean>>()
        override fun stageNext(plan: NextPlan, resource: PlayableResource) { check(!rejectStage); staged = plan }
        override fun cancelNext() { staged = null; unconfirmed?.let { unconfirmed = null; emit(RuntimeEvent.Ended(it.from)) } }
        override fun confirmNext(plan: NextPlan) { if(unconfirmed == plan) unconfirmed = null }
        override fun silence(next: LoadTicket?) { silenceCount++ }
        override fun prepare(ticket: LoadTicket, resource: PlayableResource) = Unit
        override fun setPlaying(ticket: LoadTicket, playing: Boolean, transition: VolumeTransition) { plays += ticket to playing }
        override fun seek(ticket: LoadTicket, seekId: Long, positionMs: Long) = Unit
        override fun release() = Unit
        fun emit(value: RuntimeEvent) { onEvent?.invoke(value) }
    }

    @Test fun candidateIsPrivateAndBoundaryCommitsWithoutRestartingOutput() = runTest {
        val runtime = Runtime()
        val core = PlaybackCore(backgroundScope, ContentResolver { PlayableResource(it.id) }, runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000)); runCurrent()
        val plan = runtime.staged!!
        assertEquals(b,plan.next.key)
        assertEquals(a,core.state.value.selected)
        assertEquals(listOf(a,b,c),core.state.value.queue)
        val stops = runtime.silenceCount
        runtime.emit(RuntimeEvent.Transitioned(plan,1000,0,true)); runCurrent()
        assertEquals(b,core.state.value.selected)
        assertEquals(Phase.Playing,core.state.value.phase)
        assertTrue(core.state.value.naturalTransition)
        assertEquals(stops,runtime.silenceCount)
        assertEquals(c,runtime.staged!!.next.key)
    }

    @Test fun lateResolutionAndLateNativeBoundaryCannotOverrideUserSelection() = runTest {
        val gate = CompletableDeferred<PlayableResource>()
        val runtime = Runtime()
        val core = PlaybackCore(backgroundScope,ContentResolver { if(it==b) gate.await() else PlayableResource(it.id) },runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000)); runCurrent()
        core.dispatch(PlaybackCommand.Select(c))
        gate.complete(PlayableResource("b")); runCurrent()
        assertNull(runtime.staged)
        assertEquals(c,core.state.value.selected)
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000)); runCurrent()
        core.dispatch(PlaybackCommand.SetMode(PlayMode.Loop)); runCurrent()
        val old = runtime.staged!!
        core.dispatch(PlaybackCommand.SetMode(PlayMode.RepeatOne)); runCurrent()
        runtime.emit(RuntimeEvent.Transitioned(old,1000,0,true)); runCurrent()
        assertEquals(c,core.state.value.selected)
        assertEquals(c,runtime.staged!!.next.key)
    }

    @Test fun randomExplicitIntentReplacesSpeculationWithoutChangingVisibleQueue() = runTest {
        val runtime = Runtime()
        val core = PlaybackCore(backgroundScope,ContentResolver { PlayableResource(it.id) },runtime,Random(8))
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        core.dispatch(PlaybackCommand.SetMode(PlayMode.Random))
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000)); runCurrent()
        val stale = runtime.staged!!
        core.dispatch(PlaybackCommand.PlayNext(c)); runCurrent()
        core.dispatch(PlaybackCommand.PlayNext(b)); runCurrent()
        val next = runtime.staged!!
        assertEquals(listOf(a,b,c),core.state.value.queue)
        assertEquals(b,next.next.key)
        runtime.emit(RuntimeEvent.Transitioned(stale,1000,0,true)); runCurrent()
        assertEquals(a,core.state.value.selected)
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Transitioned(next,1000,0,true)); runCurrent()
        assertEquals(listOf(a,c,b),core.state.value.queue)
        assertEquals(b,core.state.value.selected)
        assertNull(core.state.value.explicitNext)
    }

    @Test fun pauseKeepsPreparationButNeverRestoresPlayIntentAndPauseAtEndRevokes() = runTest {
        val runtime = Runtime()
        val core = PlaybackCore(backgroundScope,ContentResolver { PlayableResource(it.id) },runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b)))
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000)); runCurrent()
        val plan = runtime.staged!!
        core.dispatch(PlaybackCommand.Pause)
        assertEquals(plan,runtime.staged)
        runtime.emit(RuntimeEvent.Transitioned(plan,1000,0,true)); runCurrent()
        assertEquals(PlayIntent.Pause,core.state.value.intent)
        assertEquals(plan.next to false,runtime.plays.last())
        core.dispatch(PlaybackCommand.SetMode(PlayMode.Loop)); runCurrent()
        core.dispatch(PlaybackCommand.SetPauseAtEnd(true)); runCurrent()
        assertNull(runtime.staged)
    }

    @Test fun speculativeFailureDoesNotSkipOrStopCurrentAndRetriesOnSelection() = runTest {
        val runtime = Runtime(); var tries = 0
        val core = PlaybackCore(backgroundScope,ContentResolver { if(it==b) { tries++; error("offline") }; PlayableResource(it.id) },runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000))
        runtime.emit(RuntimeEvent.Started(core.state.value.ticket!!)); runCurrent()
        assertEquals(1,tries)
        assertEquals(a,core.state.value.selected)
        assertEquals(Phase.Playing,core.state.value.phase)
        runtime.emit(RuntimeEvent.Ended(core.state.value.ticket!!)); runCurrent()
        assertEquals(2,tries)
        assertEquals(c,core.state.value.selected)
    }
    @Test fun rejectedNativePreloadDoesNotFailTheCurrentTrack() = runTest {
        val runtime=Runtime().apply { rejectStage=true }
        val core=PlaybackCore(backgroundScope,ContentResolver { PlayableResource(it.id) },runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b)))
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000))
        runtime.emit(RuntimeEvent.Started(core.state.value.ticket!!)); runCurrent()
        assertEquals(Phase.Playing,core.state.value.phase)
        assertEquals(a,core.state.value.selected)
        assertNull(core.state.value.error)
    }

    @Test fun commandWinningNativeBoundaryRaceUsesNewestRule() = runTest {
        val runtime=Runtime()
        val core=PlaybackCore(backgroundScope,ContentResolver { PlayableResource(it.id) },runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b)))
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000)); runCurrent()
        val old=runtime.staged!!
        val command=launch(start=kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            core.dispatch(PlaybackCommand.SetMode(PlayMode.RepeatOne))
        }
        runtime.unconfirmed=old
        runtime.emit(RuntimeEvent.Transitioned(old,1000,0,true))
        runCurrent(); command.join()
        assertEquals(a,core.state.value.selected)
        assertEquals(Phase.Preparing,core.state.value.phase)
        assertNull(runtime.unconfirmed)
        assertTrue(core.state.value.epoch>old.from.epoch)
    }

}
