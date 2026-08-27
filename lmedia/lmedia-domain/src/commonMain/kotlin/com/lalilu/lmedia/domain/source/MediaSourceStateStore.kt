package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 组合式的数据源任务状态容器。
 *
 * 它把频繁变化的运行状态与可提交的完整结果分开保存，并通过 taskId 拒绝已经取消或被新任务
 * 替代的迟到结果。数据源仍然决定何时开始扫描、怎样读取数据以及如何响应配置变化。
 */
class MediaSourceStateStore {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
    private val mutableSnapshot = MutableStateFlow<Snapshot?>(null)

    private var nextTaskId = 0L
    private var activeTaskId: Long? = null
    private var currentRevision = 0L

    val state: StateFlow<SnapshotState> = mutableState.asStateFlow()
    val snapshot: StateFlow<Snapshot?> = mutableSnapshot.asStateFlow()

    suspend fun begin(
        message: String = "Loading...",
        progress: Float = 0f,
    ): Long = mutex.withLock {
        nextTaskId += 1
        activeTaskId = nextTaskId
        mutableState.value = SnapshotState.Loading(message, progress.coerceIn(0f, 1f))
        nextTaskId
    }

    suspend fun updateLoading(
        taskId: Long,
        message: String,
        progress: Float,
    ): Boolean = mutex.withLock {
        if (taskId != activeTaskId) return@withLock false
        val previousProgress = (mutableState.value as? SnapshotState.Loading)?.progress ?: 0f
        mutableState.value = SnapshotState.Loading(
            message = message,
            progress = maxOf(previousProgress, progress.coerceIn(0f, 1f)),
        )
        true
    }

    suspend fun succeed(taskId: Long, audios: List<LAudio>): Snapshot? = mutex.withLock {
        if (taskId != activeTaskId) return@withLock null
        activeTaskId = null

        currentRevision += 1
        Snapshot(
            audios = audios.distinctBy { it.id },
            revision = currentRevision,
        ).also {
            mutableSnapshot.value = it
            mutableState.value = SnapshotState.Success
        }
    }

    suspend fun fail(taskId: Long, message: String): Boolean = mutex.withLock {
        if (taskId != activeTaskId) return@withLock false
        activeTaskId = null
        mutableState.value = SnapshotState.Error(message)
        true
    }

    suspend fun cancel(taskId: Long): Boolean = mutex.withLock {
        if (taskId != activeTaskId) return@withLock false
        activeTaskId = null
        mutableState.value = if (mutableSnapshot.value != null) {
            SnapshotState.Success
        } else {
            SnapshotState.Idle
        }
        true
    }

    /** 重置运行状态但保留最近一次成功结果，避免配置 UI 的操作清空已提交数据。 */
    suspend fun reset() = mutex.withLock {
        activeTaskId = null
        mutableState.value = SnapshotState.Idle
    }
}
