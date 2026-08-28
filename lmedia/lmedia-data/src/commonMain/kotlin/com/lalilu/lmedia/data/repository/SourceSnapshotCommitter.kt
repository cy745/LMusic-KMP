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
) {
    private val mutex = Mutex()
    private var latestSnapshot: Snapshot? = null
    private var committedRevision: Long? = null
    private var state: SnapshotCommitState = SnapshotCommitState.Idle

    suspend fun submit(snapshot: Snapshot): Boolean = mutex.withLock {
        val latestRevision = latestSnapshot?.revision
        if (latestRevision != null && snapshot.revision <= latestRevision) {
            return@withLock committedRevision == snapshot.revision
        }

        latestSnapshot = snapshot
        commitLocked(snapshot)
    }

    suspend fun retry(): Boolean = mutex.withLock {
        val snapshot = latestSnapshot ?: return@withLock false
        if (committedRevision == snapshot.revision) return@withLock true
        commitLocked(snapshot)
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

    private fun publish(value: SnapshotCommitState) {
        state = value
        onStateChanged(value)
    }
}
