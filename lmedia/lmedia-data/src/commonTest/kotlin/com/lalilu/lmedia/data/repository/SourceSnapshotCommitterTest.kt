package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SourceSnapshotCommitterTest {
    @Test
    fun failedLatestSnapshotCanBeRetried() = runTest {
        var attempts = 0
        val states = mutableListOf<SnapshotCommitState>()
        val committer = SourceSnapshotCommitter(
            commit = {
                attempts++
                if (attempts == 1) error("database busy")
            },
            onStateChanged = states::add,
        )

        assertFalse(committer.submit(Snapshot(revision = 7)))
        assertIs<SnapshotCommitState.Failed>(states.last())

        assertTrue(committer.retry())
        assertEquals(2, attempts)
        assertEquals(SnapshotCommitState.Committed(7), states.last())
    }

    @Test
    fun retryWithoutSnapshotDoesNothing() = runTest {
        val committer = SourceSnapshotCommitter(
            commit = { error("must not run") },
            onStateChanged = {},
        )

        assertFalse(committer.retry())
    }

    @Test
    fun olderOrDuplicateRevisionIsNotCommittedAgain() = runTest {
        val revisions = mutableListOf<Long>()
        val committer = SourceSnapshotCommitter(
            commit = { revisions += it.revision },
            onStateChanged = {},
        )

        assertTrue(committer.submit(Snapshot(revision = 2)))
        assertTrue(committer.submit(Snapshot(revision = 2)))
        assertFalse(committer.submit(Snapshot(revision = 1)))
        assertEquals(listOf(2L), revisions)
    }

    @Test
    fun cancellationIsNotReportedAsDatabaseFailure() = runTest {
        val states = mutableListOf<SnapshotCommitState>()
        val committer = SourceSnapshotCommitter(
            commit = { throw CancellationException("screen left") },
            onStateChanged = states::add,
        )

        assertFailsWith<CancellationException> {
            committer.submit(Snapshot(revision = 1))
        }
        assertEquals(SnapshotCommitState.Idle, states.last())
    }

    @Test
    fun disabledCommitterRejectsSnapshotsUntilReactivated() = runTest {
        val revisions = mutableListOf<Long>()
        val committer = SourceSnapshotCommitter(
            commit = { revisions += it.revision },
            onStateChanged = {},
            acceptingSnapshots = false,
        )

        assertFalse(committer.submit(Snapshot(revision = 1)))
        assertFalse(committer.retry())
        committer.activate()
        assertTrue(committer.submit(Snapshot(revision = 2)))
        assertEquals(listOf(2L), revisions)
    }

    @Test
    fun deactivateWaitsForRunningCommitThenRejectsLateSnapshot() = runTest {
        val commitStarted = CompletableDeferred<Unit>()
        val finishCommit = CompletableDeferred<Unit>()
        var cleaned = false
        val committer = SourceSnapshotCommitter(
            commit = {
                commitStarted.complete(Unit)
                finishCommit.await()
            },
            onStateChanged = {},
        )

        val submitting = launch { assertTrue(committer.submit(Snapshot(revision = 1))) }
        commitStarted.await()
        val deactivating = launch { committer.deactivate { cleaned = true } }
        runCurrent()
        assertFalse(cleaned)

        finishCommit.complete(Unit)
        submitting.join()
        deactivating.join()

        assertTrue(cleaned)
        assertFalse(committer.submit(Snapshot(revision = 2)))
        assertFalse(committer.retry())
    }

    @Test
    fun failedDeactivationCleanupCanBeRetried() = runTest {
        var cleanupAttempts = 0
        val committer = SourceSnapshotCommitter(
            commit = {},
            onStateChanged = {},
        )

        assertFailsWith<IllegalStateException> {
            committer.deactivate {
                cleanupAttempts++
                error("database busy")
            }
        }
        committer.deactivate { cleanupAttempts++ }

        assertEquals(2, cleanupAttempts)
        assertFalse(committer.submit(Snapshot(revision = 1)))
    }

    @Test
    fun matchingTargetIsReconciledOnlyAfterPreviousFailure() {
        assertFalse(
            needsEnablementReconciliation(
                currentEnabled = false,
                targetEnabled = false,
                previousError = null,
            )
        )
        assertTrue(
            needsEnablementReconciliation(
                currentEnabled = false,
                targetEnabled = false,
                previousError = "cleanup failed",
            )
        )
    }

    @Test
    fun patchWaitsForTheRunningFullCommit() = runTest {
        val gate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val committer = SourceSnapshotCommitter(
            commit = {
                order += "snapshot-start"
                gate.await()
                order += "snapshot-end"
            },
            onStateChanged = {},
        )

        val submitJob = launch { committer.submit(Snapshot(revision = 1)) }
        runCurrent()
        val patchJob = launch { committer.submitPatch { order += "patch" } }
        runCurrent()

        assertEquals(listOf("snapshot-start"), order, "补丁必须等全量提交结束，不能交错")

        gate.complete(Unit)
        submitJob.join()
        patchJob.join()

        assertEquals(listOf("snapshot-start", "snapshot-end", "patch"), order)
    }

    @Test
    fun patchFailureIsReportedWithoutTouchingCommitState() = runTest {
        val states = mutableListOf<SnapshotCommitState>()
        val committer = SourceSnapshotCommitter(commit = {}, onStateChanged = states::add)

        val result = committer.submitPatch { error("database busy") }

        assertTrue(result.isFailure)
        assertEquals("database busy", result.exceptionOrNull()?.message)
        assertTrue(states.isEmpty(), "补丁失败不应写入完整快照的提交状态")
    }

    @Test
    fun patchIsRejectedWhileTheSourceIsDeactivated() = runTest {
        val committer = SourceSnapshotCommitter(commit = {}, onStateChanged = {})
        committer.deactivate { }

        var ran = false
        val result = committer.submitPatch { ran = true }

        assertTrue(result.isFailure)
        assertFalse(ran, "停用后补丁不得再入库")
    }

    @Test
    fun deactivateCoversPendingPatches() = runTest {
        val gate = CompletableDeferred<Unit>()
        val committer = SourceSnapshotCommitter(commit = {}, onStateChanged = {})
        val order = mutableListOf<String>()

        val patchJob = launch {
            committer.submitPatch {
                order += "patch-start"
                gate.await()
                order += "patch-end"
            }
        }
        runCurrent()
        val deactivateJob = launch {
            committer.deactivate { order += "cleanup" }
        }
        runCurrent()

        assertEquals(listOf("patch-start"), order, "清理必须等正在执行的补丁结束")

        gate.complete(Unit)
        patchJob.join()
        deactivateJob.join()

        assertEquals(listOf("patch-start", "patch-end", "cleanup"), order)
    }
}
