package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 串行提交单个数据源的成功快照，并保留最近结果用于失败后的原地重试。
 *
 * 每个数据源拥有独立实例和锁：同一来源的版本不会乱序，不同来源之间也不会互相等待。
 */
internal class SourceSnapshotCommitter(
    private val commit: suspend (Snapshot) -> Unit,
    private val onStateChanged: (SnapshotCommitState) -> Unit,
    acceptingSnapshots: Boolean = true,
) {
    private val mutex = Mutex()
    private var latestSnapshot: Snapshot? = null
    private var committedRevision: Long? = null
    private var state: SnapshotCommitState = SnapshotCommitState.Idle
    private var acceptingSnapshots: Boolean = acceptingSnapshots

    suspend fun submit(snapshot: Snapshot): Boolean = mutex.withLock {
        if (!acceptingSnapshots) return@withLock false
        val latestRevision = latestSnapshot?.revision
        if (latestRevision != null && snapshot.revision <= latestRevision) {
            return@withLock committedRevision == snapshot.revision
        }

        latestSnapshot = snapshot
        commitLocked(snapshot)
    }

    suspend fun retry(): Boolean = mutex.withLock {
        if (!acceptingSnapshots) return@withLock false
        val snapshot = latestSnapshot ?: return@withLock false
        if (committedRevision == snapshot.revision) return@withLock true
        commitLocked(snapshot)
    }

    suspend fun activate() = mutex.withLock {
        acceptingSnapshots = true
        latestSnapshot = null
        committedRevision = null
        publish(SnapshotCommitState.Idle)
    }

    /**
     * 与快照提交共用同一把锁，保证停用清理一定发生在已开始的提交之后，且晚到的结果不会重新入库。
     */
    suspend fun deactivate(cleanup: suspend () -> Unit) = mutex.withLock {
        acceptingSnapshots = false
        latestSnapshot = null
        committedRevision = null
        try {
            cleanup()
        } finally {
            publish(SnapshotCommitState.Idle)
        }
    }

    private suspend fun commitLocked(snapshot: Snapshot): Boolean {
        val previousState = state
        publish(SnapshotCommitState.Committing(snapshot.revision))
        return try {
            commit(snapshot)
            committedRevision = snapshot.revision
            publish(SnapshotCommitState.Committed(snapshot.revision))
            true
        } catch (cancelled: CancellationException) {
            // retryCommit 可能由页面协程触发；取消必须继续向上传递，不能伪装成数据库失败。
            publish(previousState)
            throw cancelled
        } catch (throwable: Throwable) {
            publish(
                SnapshotCommitState.Failed(
                    revision = snapshot.revision,
                    message = throwable.message ?: "Unknown database error",
                )
            )
            false
        }
    }

    /**
     * 在**同一把源级锁**内执行一次单曲增量补丁。
     *
     * 共用锁保证两件事：全量对账不会与补丁交错；[deactivate] 的清理也覆盖尚未执行的补丁。
     * 补丁失败**不写入** [SnapshotCommitState]——那是完整快照的语义，失败原因交给调用方计数与展示。
     */
    suspend fun submitPatch(patch: suspend () -> Unit): Result<Unit> = mutex.withLock {
        if (!acceptingSnapshots) {
            return@withLock Result.failure(
                IllegalStateException("Source is not accepting updates")
            )
        }
        try {
            patch()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private fun publish(value: SnapshotCommitState) {
        state = value
        onStateChanged(value)
    }
}
