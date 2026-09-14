package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.repository.PlaybackFailureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PlaybackFailureWritesTest {
    private class Store : PlaybackFailureRepository {
        var beforeRecord: suspend () -> Unit = {}
        var beforeClear: suspend () -> Unit = {}
        override val failures = MutableStateFlow<Map<String, PlaybackFailure>>(emptyMap())
        override suspend fun record(playbackId: String, failure: PlaybackFailure) {
            beforeRecord()
            failures.value += playbackId to failure
        }
        override suspend fun clearAfterSuccessfulPlayback(playbackId: String) {
            beforeClear()
            failures.value -= playbackId
        }
    }
    private val failure = PlaybackFailure(PlaybackFailureReason.Decode, 1)

    @Test fun lateSuccessCannotClearNewFailure() = runTest {
        val store = Store()
        val writes = PlaybackFailureWrites(store)
        val success = writes.register("a")
        val error = writes.register("a")
        writes.apply(error, failure)
        writes.apply(success, null)
        assertEquals(failure, store.failures.value["a"])
    }

    @Test fun lateErrorCannotOverwriteNewSuccess() = runTest {
        val store = Store()
        val writes = PlaybackFailureWrites(store)
        val error = writes.register("a")
        val success = writes.register("a")
        writes.apply(success, null)
        writes.apply(error, failure)
        assertTrue(store.failures.value.isEmpty())
    }

    @Test fun differentSongsDoNotInvalidateEachOther() = runTest {
        val store = Store()
        val writes = PlaybackFailureWrites(store)
        val a = writes.register("a")
        val b = writes.register("b")
        writes.apply(b, failure)
        writes.apply(a, failure)
        assertEquals(setOf("a", "b"), store.failures.value.keys)
    }

    @Test fun successWaitsForAnInFlightFailureAndClearsIt() = runTest {
        val store = Store()
        val writes = PlaybackFailureWrites(store)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        store.beforeRecord = {
            entered.complete(Unit)
            release.await()
        }
        val error = writes.register("a")
        val oldWrite = launch { writes.apply(error, failure) }
        entered.await()
        val success = writes.register("a")
        val newWrite = launch { writes.apply(success, null) }
        release.complete(Unit)
        oldWrite.join()
        newWrite.join()
        assertTrue(store.failures.value.isEmpty())
    }

    @Test fun aFailedStorageWriteDoesNotBlockLaterEvents() = runTest {
        val store = Store()
        val writes = PlaybackFailureWrites(store)
        store.beforeRecord = { error("Storage unavailable") }
        val failed = writes.apply(writes.register("a"), failure)
        assertTrue(failed.isFailure)
        store.beforeRecord = {}
        writes.apply(writes.register("a"), failure)
        assertEquals(failure, store.failures.value["a"])
        writes.apply(writes.register("a"), null)
        assertTrue(store.failures.value.isEmpty())
    }

    @Test fun aFailedClearIsReportedAndCanBeRetried() = runTest {
        val store = Store()
        val writes = PlaybackFailureWrites(store)
        writes.apply(writes.register("a"), failure)
        store.beforeClear = { error("Storage unavailable") }
        assertTrue(writes.apply(writes.register("a"), null).isFailure)
        assertEquals(failure, store.failures.value["a"])
        store.beforeClear = {}
        assertTrue(writes.apply(writes.register("a"), null).isSuccess)
        assertTrue(store.failures.value.isEmpty())
    }

    @Test fun cancellationIsNotConvertedIntoAStorageFailure() = runTest {
        val store = Store()
        val writes = PlaybackFailureWrites(store)
        store.beforeRecord = { throw CancellationException("Playback was stopped") }
        assertFailsWith<CancellationException> {
            writes.apply(writes.register("a"), failure)
        }
        store.beforeRecord = {}
        assertTrue(writes.apply(writes.register("a"), failure).isSuccess)
    }
}
