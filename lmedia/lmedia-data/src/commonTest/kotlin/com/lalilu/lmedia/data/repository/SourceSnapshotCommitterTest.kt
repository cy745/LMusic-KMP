package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
}
