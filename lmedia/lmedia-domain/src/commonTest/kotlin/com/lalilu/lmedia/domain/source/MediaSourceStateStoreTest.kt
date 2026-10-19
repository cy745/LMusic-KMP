package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaSourceStateStoreTest {
    @Test
    fun tryBeginEntersLoadingSynchronouslyAndRejectsDuplicateTask() = runTest {
        val store = MediaSourceStateStore()

        val firstTask = store.tryBegin(message = "Scanning")
        val duplicateTask = store.tryBegin(message = "Duplicate")

        assertEquals(1L, firstTask)
        assertNull(duplicateTask)
        assertEquals("Scanning", assertIs<SnapshotState.Loading>(store.state.value).message)

        store.succeed(firstTask!!, listOf(audio("song")))
        assertEquals(2L, store.tryBegin())
    }

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

    @Test
    fun resetInvalidatesTaskBeforeItsCoroutineStarts() = runTest {
        val store = MediaSourceStateStore()
        val task = store.tryBegin()!!

        store.reset()

        assertFalse(store.isActive(task))
        assertNull(store.succeed(task, listOf(audio("late"))))
        assertIs<SnapshotState.Idle>(store.state.value)
    }

    @Test
    fun activeTaskCanBeCheckedBeforeStartingWork() = runTest {
        val store = MediaSourceStateStore()
        val task = store.tryBegin()!!

        assertTrue(store.isActive(task))
        store.cancel(task)
        assertFalse(store.isActive(task))
    }

    @Test
    fun cancellationBeforeFirstDispatchReleasesReservedTask() = runTest {
        val store = MediaSourceStateStore()
        val task = store.tryBegin()!!
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                yield()
                if (!store.isActive(task)) return@launch
                error("work must not start")
            } catch (cancelled: CancellationException) {
                store.cancel(task)
                throw cancelled
            }
        }

        worker.cancel()
        worker.join()

        assertIs<SnapshotState.Idle>(store.state.value)
        assertEquals(2L, store.tryBegin())
    }

    @Test
    fun publishUpdateAdvancesRevisionWithoutChangingScanState() = runTest {
        val store = MediaSourceStateStore()
        store.succeed(store.begin(), listOf(audio("one")))

        val updated = store.publishUpdate { current ->
            current.map { it.copy(title = "enriched") }
        }

        assertEquals(2L, updated?.revision, "发布更新必须推进 revision，否则数据库不会收到")
        assertEquals(listOf("enriched"), updated?.audios?.map { it.title })
        assertIs<SnapshotState.Success>(store.state.value, "补全元数据不应把状态刷成「同步中」")
    }

    @Test
    fun publishUpdateIsIgnoredBeforeTheFirstSuccessfulSnapshot() = runTest {
        val store = MediaSourceStateStore()
        store.begin() // 扫描进行中，但还没有任何成功结果

        val published = store.publishUpdate { listOf(audio("late")) }

        assertNull(published, "首个成功快照之前不得发布，否则数据库会把该来源的歌曲全部标记为不可用")
        assertNull(store.snapshot.value)
        assertIs<SnapshotState.Loading>(store.state.value)
    }

    @Test
    fun publishUpdateAppliesTransformUnderTheSameLockAsSucceed() = runTest {
        val store = MediaSourceStateStore()
        store.succeed(store.begin(), listOf(audio("one")))
        val scanTask = store.begin() // 新扫描进行中

        val published = store.publishUpdate { current -> current + audio("one-enriched") }

        assertEquals(listOf("one", "one-enriched"), published?.audios?.map(LAudio::id))
        assertIs<SnapshotState.Loading>(store.state.value, "扫描状态不受补丁影响")

        // 扫描自己的结果仍然拥有最终解释权：它必须包含补丁，否则会把补全结果覆盖掉
        val finalSnapshot = store.succeed(scanTask, listOf(audio("one"), audio("two")))
        assertEquals(listOf("one", "two"), finalSnapshot?.audios?.map(LAudio::id))
        assertEquals(3L, finalSnapshot?.revision)
    }

    @Test
    fun publishUpdateKeepsTheFirstOccurrenceOfADuplicateIdLikeSucceed() = runTest {
        val store = MediaSourceStateStore()
        store.succeed(store.begin(), listOf(audio("one").copy(title = "original")))

        // 追加同 id 的新值：按 succeed 的既有语义（distinctBy 保留先出现者）不会被采纳。
        // 需要替换时，transform 必须按 id 就地替换——这条断言把该约束钉住，避免以后误用。
        val appended = store.publishUpdate { current -> current + audio("one").copy(title = "appended") }
        assertEquals(1, appended?.audios?.size)
        assertEquals("original", appended?.audios?.single()?.title)

        val replaced = store.publishUpdate { current ->
            current.map { if (it.id == "one") it.copy(title = "replaced") else it }
        }
        assertEquals(1, replaced?.audios?.size)
        assertEquals("replaced", replaced?.audios?.single()?.title)
    }

    private fun audio(id: String) = LAudio(id = id, mediaSourceName = "test")
}
