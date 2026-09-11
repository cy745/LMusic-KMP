package com.lalilu.lplayer.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LatestPlaybackCommandTest {
    @Test fun pauseCancelsPendingLoadBeforeItCanPlay() = runTest {
        val commands = LatestPlaybackCommand()
        val resolved = CompletableDeferred<Unit>()
        var played = false
        var paused = false
        val loading = launch { commands.run { resolved.await(); played = true } }
        runCurrent()
        commands.run { paused = true }
        resolved.complete(Unit)
        loading.join()
        assertTrue(loading.isCancelled)
        assertFalse(played)
        assertTrue(paused)
    }

    @Test fun thirdCommandStillWaitsForFirstCommandsCleanup() = runTest {
        val commands = LatestPlaybackCommand()
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = launch {
            commands.run {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { cleanup.await(); events += "cleaned" } }
            }
        }
        runCurrent()
        val second = launch { commands.run { events += "second" } }
        runCurrent()
        val third = launch { commands.run { events += "third" } }
        runCurrent()
        assertTrue(events.isEmpty())
        cleanup.complete(Unit)
        joinAll(first, second, third)
        assertEquals(listOf("cleaned", "third"), events)
        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
    }

    @Test fun failureDoesNotPreventTheNextCommand() = runTest {
        val commands = LatestPlaybackCommand()
        assertFailsWith<IllegalStateException> { commands.run { error("native failure") } }
        assertEquals(42, commands.run { 42 })
    }

    @Test fun cancellingCallerCancelsItsCommandAndDoesNotCancelOwnerScope() = runTest {
        val commands = LatestPlaybackCommand()
        var cleaned = false
        val caller = launch {
            commands.run { try { awaitCancellation() } finally { cleaned = true } }
        }
        runCurrent()
        caller.cancelAndJoin()
        assertTrue(cleaned)
        assertTrue(coroutineContext.isActive)
        assertEquals("retry", commands.run { "retry" })
    }
}
