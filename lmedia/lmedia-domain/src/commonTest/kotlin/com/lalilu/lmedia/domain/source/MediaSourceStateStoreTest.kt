package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class MediaSourceStateStoreTest {
    @Test
    fun staleTaskCannotReplaceNewerResult() = runTest {
        val store = MediaSourceStateStore()
        val firstTask = store.begin()
        val secondTask = store.begin()

        assertNull(store.succeed(firstTask, listOf(audio("old"))))
        val result = store.succeed(secondTask, listOf(audio("new")))

        assertEquals(listOf("new"), result?.audios?.map(LAudio::id))
        assertEquals(1L, result?.revision)
        assertEquals(result, store.snapshot.value)
        assertIs<SnapshotState.Success>(store.state.value)
    }

    @Test
    fun failureKeepsLatestSuccessfulResult() = runTest {
        val store = MediaSourceStateStore()
        val successTask = store.begin()
        val success = store.succeed(successTask, listOf(audio("song")))
        val failedTask = store.begin()

        store.fail(failedTask, "scan failed")

        assertEquals(success, store.snapshot.value)
        assertEquals("scan failed", assertIs<SnapshotState.Error>(store.state.value).message)
    }

    @Test
    fun revisionsOnlyAdvanceForAcceptedSuccesses() = runTest {
        val store = MediaSourceStateStore()
        val firstTask = store.begin()
        assertEquals(1L, store.succeed(firstTask, listOf(audio("one")))?.revision)

        val cancelledTask = store.begin()
        store.cancel(cancelledTask)
        assertNull(store.succeed(cancelledTask, listOf(audio("cancelled"))))

        val secondTask = store.begin()
        assertEquals(2L, store.succeed(secondTask, listOf(audio("two")))?.revision)
    }

    @Test
    fun terminalTaskRejectsLateCallbacks() = runTest {
        val store = MediaSourceStateStore()
        val successfulTask = store.begin()
        store.succeed(successfulTask, listOf(audio("song")))

        assertFalse(store.updateLoading(successfulTask, "late progress", 1f))
        assertFalse(store.fail(successfulTask, "late failure"))
        assertIs<SnapshotState.Success>(store.state.value)

        val failedTask = store.begin()
        store.fail(failedTask, "expected failure")

        assertNull(store.succeed(failedTask, listOf(audio("late song"))))
        assertIs<SnapshotState.Error>(store.state.value)
    }

    @Test
    fun cancellingRefreshRestoresLatestSuccessfulState() = runTest {
        val store = MediaSourceStateStore()

        val initialTask = store.begin()
        store.succeed(initialTask, listOf(audio("song")))
        val refreshTask = store.begin()
        store.cancel(refreshTask)

        assertIs<SnapshotState.Success>(store.state.value)
        assertEquals(listOf("song"), store.snapshot.value?.audios?.map(LAudio::id))
    }

    @Test
    fun cancellingFirstLoadReturnsToIdle() = runTest {
        val store = MediaSourceStateStore()
        val task = store.begin()

        store.cancel(task)

        assertIs<SnapshotState.Idle>(store.state.value)
        assertNull(store.snapshot.value)
    }

    @Test
    fun acceptedEmptyResultIsARealSuccessfulSnapshot() = runTest {
        val store = MediaSourceStateStore()
        val task = store.begin()

        val result = store.succeed(task, emptyList())

        assertEquals(emptyList(), result?.audios)
        assertEquals(1L, result?.revision)
        assertIs<SnapshotState.Success>(store.state.value)
    }

    private fun audio(id: String) = LAudio(id = id, mediaSourceName = "test")
}
