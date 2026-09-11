package com.lalilu.lplayer.extensions

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeFadeHelperTest {
    @BeforeTest fun prepareMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun restoreMain() { Dispatchers.resetMain() }

    @Test fun pauseDoesNotCompleteUntilNativeCallbackCompletes() = runTest {
        val helper = VolumeFadeHelper(onSetVolume = {}, fadeEnabled = { false })
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val pause = launch { helper.pauseAndAwait { entered.complete(Unit); finish.await() } }
        entered.await()
        assertFalse(pause.isCompleted)
        finish.complete(Unit)
        pause.join()
        assertFalse(pause.isCancelled)
    }

    @Test fun nativePauseFailureReachesTheCaller() = runTest {
        val failure = IllegalStateException("native pause failed")
        val helper = VolumeFadeHelper(onSetVolume = {}, fadeEnabled = { false })
        val caught = assertFailsWith<IllegalStateException> {
            helper.pauseAndAwait { throw failure }
        }
        // Coroutine stack-trace recovery may copy the exception and retain its original cause.
        assertEquals(failure.message, caught.message)
        assertTrue(caught === failure || caught.cause === failure)
    }

    @Test fun cancellingCallerCancelsNativePauseWork() = runTest {
        val helper = VolumeFadeHelper(onSetVolume = {}, fadeEnabled = { false })
        val entered = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val pause = launch {
            helper.pauseAndAwait {
                try { entered.complete(Unit); awaitCancellation() }
                finally { cleaned.complete(Unit) }
            }
        }
        entered.await()
        pause.cancelAndJoin()
        assertTrue(cleaned.isCompleted)
    }

    @Test fun playCancelsPreviousPauseEvenWhenFadeIsDisabled() = runTest {
        val helper = VolumeFadeHelper(onSetVolume = {}, fadeEnabled = { false })
        val entered = CompletableDeferred<Unit>()
        var continued = false
        val pause = launch {
            helper.pauseAndAwait { entered.complete(Unit); awaitCancellation() }
            continued = true
        }
        entered.await()
        helper.play()
        pause.join()
        assertTrue(pause.isCancelled)
        assertFalse(continued)
    }

    @Test fun nativePauseRunsAfterTheFadeReachesZero() = runTest {
        var volume = 0f
        val helper = VolumeFadeHelper(onSetVolume = { volume = it })
        helper.updateVolume(100f)
        helper.pauseAndAwait { assertEquals(0f, volume) }
    }
}
