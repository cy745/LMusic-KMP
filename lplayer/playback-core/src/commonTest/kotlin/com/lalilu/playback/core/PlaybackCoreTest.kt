package com.lalilu.playback.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoreTest {
    private val a = TrackKey("source", "a")
    private val b = TrackKey("source", "b")
    private val c = TrackKey("source", "c")
    private val d = TrackKey("source", "d")

    private class Runtime : AudioRuntime {
        override var onEvent: ((RuntimeEvent) -> Unit)? = null
        val prepared = mutableListOf<LoadTicket>()
        val plays = mutableListOf<Pair<LoadTicket, Boolean>>()
        val smoothing = mutableListOf<Boolean>()
        var seek: Triple<LoadTicket, Long, Long>? = null
        override fun silence(next: LoadTicket?) = Unit
        override fun prepare(ticket: LoadTicket, resource: PlayableResource) { prepared += ticket }
        override fun setPlaying(ticket: LoadTicket, playing: Boolean, smooth: Boolean) { plays += ticket to playing; smoothing += smooth }
        override fun seek(ticket: LoadTicket, seekId: Long, positionMs: Long) { seek = Triple(ticket, seekId, positionMs) }
        override fun release() = Unit
        fun emit(event: RuntimeEvent) { onEvent?.invoke(event) }
    }
    private fun TestScope.core(runtime: Runtime, resolver: ContentResolver = ContentResolver { PlayableResource(it.id) }) =
        PlaybackCore(backgroundScope, resolver, runtime, Random(7))

    @Test fun quickSelectionAndPauseRejectLateEvents() = runTest {
        val pendingB = CompletableDeferred<PlayableResource>()
        val runtime = Runtime()
        val core = core(runtime, ContentResolver { if (it == b) pendingB.await() else PlayableResource(it.id) })
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        core.dispatch(PlaybackCommand.Play)
        core.dispatch(PlaybackCommand.Select(b))
        val bTicket = core.state.value.ticket!!
        core.dispatch(PlaybackCommand.Select(c))
        core.dispatch(PlaybackCommand.Pause)
        runCurrent()
        val cTicket = core.state.value.ticket!!
        pendingB.complete(PlayableResource("b"))
        runtime.emit(RuntimeEvent.Prepared(bTicket, 100))
        runtime.emit(RuntimeEvent.Started(bTicket))
        runtime.emit(RuntimeEvent.Prepared(cTicket, 3000))
        runCurrent()
        assertEquals(c, core.state.value.selected)
        assertEquals(Phase.ReadyPaused, core.state.value.phase)
        assertFalse(runtime.plays.any { it.first == bTicket && it.second })
        assertEquals(cTicket to false, runtime.plays.last())
    }

    @Test fun pauseThenFailureNeverNavigates() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b)))
        core.dispatch(PlaybackCommand.Play)
        core.dispatch(PlaybackCommand.Pause)
        runtime.emit(RuntimeEvent.Failed(core.state.value.ticket!!,"offline")); runCurrent()
        assertEquals(a,core.state.value.selected)
        assertEquals(Phase.Failed,core.state.value.phase)
        assertEquals(PlayIntent.Pause,core.state.value.intent)
    }

    @Test fun repeatOneFailureStopsRegardlessOfAutoSkip() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b)))
        core.dispatch(PlaybackCommand.SetMode(PlayMode.RepeatOne))
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Failed(core.state.value.ticket!!,"bad")); runCurrent()
        assertEquals(a,core.state.value.selected)
        assertEquals(Phase.Failed,core.state.value.phase)
    }

    @Test fun failureRoundVisitsEachTrackOnceAndPlayRetriesCurrent() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        core.dispatch(PlaybackCommand.Play)
        val visited=mutableListOf<TrackKey>()
        repeat(3) {
            visited += core.state.value.selected!!
            runtime.emit(RuntimeEvent.Failed(core.state.value.ticket!!,"bad")); runCurrent()
        }
        assertEquals(listOf(a,b,c),visited)
        assertEquals(Phase.Failed,core.state.value.phase)
        val previous=core.state.value.ticket
        core.dispatch(PlaybackCommand.Play)
        assertEquals(c,core.state.value.selected)
        assertNotEquals(previous,core.state.value.ticket)
    }

    @Test fun naturalSequentialEndStaysButManualNavigationWraps() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b), b))
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Ended(core.state.value.ticket!!)); runCurrent()
        assertEquals(b,core.state.value.selected)
        assertEquals(Phase.Ended,core.state.value.phase)
        core.dispatch(PlaybackCommand.Play)
        assertEquals(b,core.state.value.selected)
        core.dispatch(PlaybackCommand.Next)
        assertEquals(a,core.state.value.selected)
        core.dispatch(PlaybackCommand.Previous)
        assertEquals(b,core.state.value.selected)
    }

    @Test fun ordinaryPlayNextPermanentlyMovesAndDeduplicates() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c,d)))
        core.dispatch(PlaybackCommand.PlayNext(d))
        assertEquals(listOf(a,d,b,c),core.state.value.queue)
        core.dispatch(PlaybackCommand.PlayNext(c))
        core.dispatch(PlaybackCommand.PlayNext(c))
        assertEquals(listOf(a,c,d,b),core.state.value.queue)
        val ticket=core.state.value.ticket
        core.dispatch(PlaybackCommand.PlayNext(a))
        assertEquals(ticket,core.state.value.ticket)
    }

    @Test fun randomExplicitNextReplacesAndIsConsumedOnlyWhenStarted() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c,d)))
        core.dispatch(PlaybackCommand.SetMode(PlayMode.Random))
        core.dispatch(PlaybackCommand.PlayNext(b))
        core.dispatch(PlaybackCommand.PlayNext(c))
        assertEquals(listOf(a,b,c,d),core.state.value.queue)
        core.dispatch(PlaybackCommand.Next)
        assertEquals(c,core.state.value.selected)
        assertEquals(c,core.state.value.explicitNext)
        assertEquals(PlayIntent.Pause,core.state.value.intent)
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,3000)); runCurrent()
        runtime.emit(RuntimeEvent.Started(core.state.value.ticket!!)); runCurrent()
        assertNull(core.state.value.explicitNext)
        val state=core.state.value
        assertEquals(a,state.queue[(state.index+1)%state.queue.size])
    }

    @Test fun randomPreviousReturnsToSongBelowCurrentWithoutRestoringQueue() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c,d)))
        core.dispatch(PlaybackCommand.SetMode(PlayMode.Random))
        core.dispatch(PlaybackCommand.PlayNext(c))
        core.dispatch(PlaybackCommand.Next)
        val reordered=core.state.value.queue
        core.dispatch(PlaybackCommand.Previous)
        assertEquals(a,core.state.value.selected)
        assertEquals(reordered,core.state.value.queue)
        assertNull(core.state.value.explicitNext)
    }

    @Test fun explicitIntentClearsOnPreviousDirectSelectionAndQueueEdit() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        core.dispatch(PlaybackCommand.SetMode(PlayMode.Random))
        core.dispatch(PlaybackCommand.PlayNext(c))
        core.dispatch(PlaybackCommand.Select(a))
        assertEquals(c,core.state.value.explicitNext)
        core.dispatch(PlaybackCommand.Previous)
        assertNull(core.state.value.explicitNext)
        core.dispatch(PlaybackCommand.PlayNext(c))
        core.dispatch(PlaybackCommand.Select(a))
        assertNull(core.state.value.explicitNext)
        core.dispatch(PlaybackCommand.PlayNext(c))
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(c,b,a)))
        assertNull(core.state.value.explicitNext)
    }

    @Test fun replacingAndReorderingPreservesLoadedIdentityAndProgress() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c), b))
        val ticket=core.state.value.ticket!!
        runtime.emit(RuntimeEvent.Prepared(ticket,9000)); runCurrent()
        runtime.emit(RuntimeEvent.Position(ticket,2345,9000,3000)); runCurrent()
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(c,b,a,b)))
        assertEquals(listOf(c,b,a),core.state.value.queue)
        assertEquals(ticket,core.state.value.ticket)
        assertEquals(2345,core.state.value.positionMs)
        core.dispatch(PlaybackCommand.Remove(b))
        assertEquals(a,core.state.value.selected)
        assertEquals(PlayIntent.Pause,core.state.value.intent)
    }

    @Test fun clearingThenAddingDoesNotInheritPlayIntent() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a)))
        core.dispatch(PlaybackCommand.Play)
        core.dispatch(PlaybackCommand.Remove(a))
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(b)))
        assertEquals(PlayIntent.Pause,core.state.value.intent)
        assertEquals(Phase.Preparing,core.state.value.phase)
    }

    @Test fun seekConfirmationCannotOverrideNewSeekOrPause() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a)))
        val ticket=core.state.value.ticket!!
        runtime.emit(RuntimeEvent.Prepared(ticket,9000)); runCurrent()
        core.dispatch(PlaybackCommand.Play)
        core.dispatch(PlaybackCommand.Seek(1000))
        val first=runtime.seek!!
        core.dispatch(PlaybackCommand.Seek(6000))
        val second=runtime.seek!!
        core.dispatch(PlaybackCommand.Pause)
        runtime.emit(RuntimeEvent.SeekSettled(ticket,first.second,1000)); runCurrent()
        assertEquals(6000,core.state.value.positionMs)
        assertEquals(second.second,core.state.value.pendingSeekId)
        runtime.emit(RuntimeEvent.SeekSettled(ticket,second.second,6000)); runCurrent()
        assertEquals(Phase.ReadyPaused,core.state.value.phase)
        assertNull(core.state.value.pendingSeekId)
        assertEquals(ticket to false,runtime.plays.last())
    }

    @Test fun sourceQualifiedIdentityDoesNotMergeSameIds() = runTest {
        val core=core(Runtime())
        val other=TrackKey("other","a")
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,other,a)))
        assertEquals(listOf(a,other),core.state.value.queue)
    }

    @Test fun naturalTransitionDoesNotRequestFadeButManualTransitionDoes() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c)))
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000)); runCurrent()
        assertTrue(runtime.smoothing.last())
        runtime.emit(RuntimeEvent.Ended(core.state.value.ticket!!)); runCurrent()
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000)); runCurrent()
        assertFalse(runtime.smoothing.last())
        core.dispatch(PlaybackCommand.Next)
        runtime.emit(RuntimeEvent.Prepared(core.state.value.ticket!!,1000)); runCurrent()
        assertTrue(runtime.smoothing.last())
    }

    @Test fun repeatOneNaturalRepeatAndManualNextAreDifferent() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b)))
        core.dispatch(PlaybackCommand.SetMode(PlayMode.RepeatOne))
        core.dispatch(PlaybackCommand.PlayNext(b))
        core.dispatch(PlaybackCommand.Play)
        val first=core.state.value.ticket!!
        runtime.emit(RuntimeEvent.Ended(first)); runCurrent()
        assertEquals(a,core.state.value.selected)
        assertNotEquals(first,core.state.value.ticket)
        core.dispatch(PlaybackCommand.Next)
        assertEquals(b,core.state.value.selected)
    }

    @Test fun randomFailuresDoNotRetryAnyTrackInTheSameRound() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a,b,c,d)))
        core.dispatch(PlaybackCommand.SetMode(PlayMode.Random))
        core.dispatch(PlaybackCommand.Play)
        val visited=mutableSetOf<TrackKey>()
        repeat(4) {
            assertTrue(visited.add(core.state.value.selected!!))
            runtime.emit(RuntimeEvent.Failed(core.state.value.ticket!!,"bad")); runCurrent()
        }
        assertEquals(setOf(a,b,c,d),visited)
        assertEquals(Phase.Failed,core.state.value.phase)
    }

    @Test fun lateEventFromPreviousLoadOfSameTrackCannotStartRetry() = runTest {
        val runtime=Runtime(); val core=core(runtime)
        core.dispatch(PlaybackCommand.ReplaceQueue(listOf(a)))
        val old=core.state.value.ticket!!
        runtime.emit(RuntimeEvent.Failed(old,"bad")); runCurrent()
        core.dispatch(PlaybackCommand.Play)
        runtime.emit(RuntimeEvent.Prepared(old,5000))
        runtime.emit(RuntimeEvent.Started(old)); runCurrent()
        assertEquals(Phase.Preparing,core.state.value.phase)
        assertTrue(runtime.plays.none { it.first==old && it.second })
    }
}
