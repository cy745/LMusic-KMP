package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EnabledSourceOperationTest {
    @Test fun disableWaitsForCommitAndRollbackWithoutBlockingSnapshotCommitter() = runTest {
        val sourceMutex = Mutex()
        var enabled = true
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val committer = SourceSnapshotCommitter(commit = { events += "commit" }, onStateChanged = {})
        val operation = async {
            sourceMutex.runWhileSourceEnabled({ enabled }) {
                events += "operation"
                release.await()
                committer.submit(Snapshot(revision = 1))
                // Model the recovery commit while still owning the source operation boundary.
                committer.submit(Snapshot(revision = 2))
                events += "restored"
            }
        }
        runCurrent()
        val disable = async {
            sourceMutex.withLock {
                enabled = false
                committer.deactivate { events += "disabled" }
            }
        }
        runCurrent()
        assertFalse(disable.isCompleted)
        release.complete(Unit)
        operation.await()
        disable.await()
        assertEquals(listOf("operation", "commit", "commit", "restored", "disabled"), events)
    }

    @Test fun queuedOperationRechecksEnabledAfterDisableWins() = runTest {
        val mutex = Mutex(locked = true)
        var enabled = true
        val result = async {
            runCatching { mutex.runWhileSourceEnabled({ enabled }) { error("Must not touch files") } }
        }
        runCurrent()
        enabled = false
        mutex.unlock()
        assertEquals("数据源已停用，请启用后再操作文件", result.await().exceptionOrNull()!!.message)
    }

    @Test fun cancellationReleasesBoundaryOnlyAfterNonCancellableRecovery() = runTest {
        val mutex = Mutex()
        val recovery = CompletableDeferred<Unit>()
        val operation = launch {
            mutex.runWhileSourceEnabled({ true }) {
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { recovery.await() }
                }
            }
        }
        runCurrent()
        operation.cancel()
        runCurrent()
        val disable = async { mutex.withLock { true } }
        runCurrent()
        assertFalse(disable.isCompleted)
        recovery.complete(Unit)
        operation.cancelAndJoin()
        assertTrue(disable.await())
    }

    @Test fun unrelatedSourceCanProceedDuringLongOperation() = runTest {
        val first = Mutex(locked = true)
        val second = Mutex()
        assertEquals(42, second.runWhileSourceEnabled({ true }) { 42 })
        first.unlock()
    }
}
