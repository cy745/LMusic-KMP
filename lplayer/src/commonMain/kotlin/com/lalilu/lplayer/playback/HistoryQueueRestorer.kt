package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface HistoryRestoreState {
    data object Inactive : HistoryRestoreState

    data class Pending(
        val originalIds: List<String>,
        val resolvedIds: List<String> = emptyList(),
        val pendingIds: Set<String> = originalIds.toSet(),
        val currentId: String?,
        /** 历史当前项及其 position 已经真正应用到播放器，或已明确回退到用户可操作的队列。 */
        val currentRestored: Boolean = currentId == null,
    ) : HistoryRestoreState

    data class Complete(val originalIds: List<String>) : HistoryRestoreState
    data object Cancelled : HistoryRestoreState
}

/**
 * 持续把历史 ID 解析成歌曲，并在数据库后续补充数据时按原顺序填回队列。
 *
 * 未解析 ID 只存在于恢复状态中，不伪造 LAudio。只要用户没有主动改变队列内容，恢复任务就会
 * 保留原始 ID；切歌等仅改变 index 的操作不会中断补全。
 */
class HistoryQueueRestorer(
    private val snapshot: PlaybackHistory.HistorySnapshot,
    private val repository: AudioRepository,
    private val restoreSettled: Flow<Boolean> = emptyFlow(),
) {
    private val originalIds = snapshot.ids
    private val originalCurrentIndex = snapshot.index.takeIf { it in originalIds.indices }
    private var currentId = snapshot.ids.getOrNull(snapshot.index)
    private val mutableState = MutableStateFlow<HistoryRestoreState>(
        HistoryRestoreState.Pending(
            originalIds = originalIds,
            currentId = currentId,
        )
    )

    val state: StateFlow<HistoryRestoreState> = mutableState.asStateFlow()

    private var resolvedIds: List<String> = emptyList()
    private var currentDelivered = false
    private var restoreJob: Job? = null
    private var queueWatcherJob: Job? = null
    private var settlementWatcherJob: Job? = null
    private var sourcesSettled = false
    private val stateMutex = Mutex()

    fun start(
        scope: CoroutineScope,
        queue: PlayableQueue,
        onCurrentResolved: suspend (PlaybackHistory.HistorySnapshot) -> Unit,
    ) {
        if (restoreJob != null || originalIds.isEmpty()) return

        queueWatcherJob = scope.launch {
            queue.expandedItems.collect { queueState ->
                val shouldStop = stateMutex.withLock {
                    if (mutableState.value !is HistoryRestoreState.Pending) return@withLock false
                    if (
                        queueState.updateReason is QueueUpdateReason.HistoryRestore ||
                        queueState.updateReason is QueueUpdateReason.Sync
                    ) return@withLock false

                    val queueIds = queueState.list.map { it.id }
                    when {
                        queueIds != resolvedIds -> {
                            mutableState.value = HistoryRestoreState.Cancelled
                            true
                        }

                        // 初始空队列不是一次用户切歌，至少恢复出一首后才判断 index 变化。
                        resolvedIds.isNotEmpty() &&
                            currentId != null &&
                            queueState.updateReason !is QueueUpdateReason.Sync &&
                            queueState.currentItem()?.id != currentId -> {
                            // 用户已在部分队列中主动切歌：继续补全，但不再跳回旧的历史项。
                            currentDelivered = true
                            currentId = null
                            (mutableState.value as? HistoryRestoreState.Pending)?.let { pending ->
                                mutableState.value = pending.copy(
                                    currentId = null,
                                    currentRestored = true,
                                )
                            }
                            false
                        }

                        else -> false
                    }
                }
                if (shouldStop) stopCollectors()
            }
        }

        settlementWatcherJob = scope.launch {
            restoreSettled.collect { settled ->
                stateMutex.withLock {
                    sourcesSettled = settled
                    fallbackFromMissingCurrentLocked()
                }
            }
        }

        restoreJob = scope.launch {
            repository.getAudios(originalIds).collect { audios ->
                val step = stateMutex.withLock {
                    if (mutableState.value !is HistoryRestoreState.Pending) {
                        return@withLock RestoreStep.Ignore
                    }

                    val byId = audios.associateBy { it.id }
                    val resolved = originalIds.mapNotNull(byId::get)
                    val nextResolvedIds = resolved.map { it.id }
                    val pendingIds = originalIds.toSet() - nextResolvedIds.toSet()

                    // 先发布恢复状态，确保队列录制器看到 HistoryRestore 更新时保留完整原始 ID。
                    val previousPending = mutableState.value as HistoryRestoreState.Pending
                    mutableState.value = previousPending.copy(
                        resolvedIds = nextResolvedIds,
                        pendingIds = pendingIds,
                        currentId = currentId,
                    )

                    // 数据库尚未解析出任何历史项时保持标准空队列，避免制造 index = -1 的空更新。
                    if (nextResolvedIds.isEmpty() && resolvedIds.isEmpty()) {
                        return@withLock RestoreStep.Ignore
                    }

                    val targetCurrentId = currentId
                    val currentResolvedIndex = if (
                        targetCurrentId != null &&
                        originalCurrentIndex != null &&
                        byId.containsKey(targetCurrentId)
                    ) {
                        originalIds
                            .take(originalCurrentIndex)
                            .count(byId::containsKey)
                    } else {
                        -1
                    }
                    val previousCurrentId = queue.stateSnapshot().currentItem()?.id
                    val previousResolvedIndex = previousCurrentId?.let(nextResolvedIds::indexOf) ?: -1
                    val targetIndex = when {
                        currentResolvedIndex >= 0 && !currentDelivered -> currentResolvedIndex
                        previousResolvedIndex >= 0 -> previousResolvedIndex
                        else -> 0
                    }

                    queue.update(
                        updateReason = QueueUpdateReason.HistoryRestore,
                        predicate = { current ->
                            current.list.map { it.id } == resolvedIds ||
                                current.updateReason is QueueUpdateReason.Sync
                        },
                    ) {
                        replaceAll(resolved, targetIndex)
                    }

                    val applied = queue.stateSnapshot().let { queueState ->
                        queueState.updateReason is QueueUpdateReason.HistoryRestore &&
                            queueState.list.map { it.id } == nextResolvedIds
                    }
                    if (!applied) {
                        mutableState.value = HistoryRestoreState.Cancelled
                        return@withLock RestoreStep.Stop
                    }

                    resolvedIds = nextResolvedIds
                    val shouldNotifyCurrent = !currentDelivered && currentResolvedIndex >= 0
                    if (shouldNotifyCurrent) currentDelivered = true

                    fallbackFromMissingCurrentLocked()

                    if (pendingIds.isEmpty() && !shouldNotifyCurrent) {
                        mutableState.value = HistoryRestoreState.Complete(originalIds)
                    }
                    RestoreStep(
                        notifyCurrent = shouldNotifyCurrent,
                        shouldStop = pendingIds.isEmpty() && !shouldNotifyCurrent,
                    )
                }

                if (step.notifyCurrent) {
                    onCurrentResolved(snapshot)
                    val completed = stateMutex.withLock { markCurrentRestoredLocked() }
                    if (completed) stopCollectors()
                } else if (step.shouldStop) {
                    stopCollectors()
                }
            }
        }
    }

    /**
     * 所有数据源都已结束首轮处理后，历史当前项仍不存在，就保留已恢复队列并回退到其中的当前项。
     * 未解析 ID 仍继续观察数据库，后续手动配置数据源时依然可以补回，但不会再强制跳回旧歌曲。
     */
    private fun fallbackFromMissingCurrentLocked() {
        val pending = mutableState.value as? HistoryRestoreState.Pending ?: return
        if (!sourcesSettled || currentId == null || currentId in resolvedIds || resolvedIds.isEmpty()) return

        currentDelivered = true
        currentId = null
        mutableState.value = pending.copy(
            currentId = null,
            currentRestored = true,
        )
    }

    /** 在平台播放器完成历史 current/position 应用后，才允许位置录制器开始覆盖持久化值。 */
    private fun markCurrentRestoredLocked(): Boolean {
        val pending = mutableState.value as? HistoryRestoreState.Pending ?: return false
        if (pending.pendingIds.isEmpty()) {
            mutableState.value = HistoryRestoreState.Complete(originalIds)
            return true
        }

        mutableState.value = pending.copy(currentRestored = true)
        return false
    }

    private fun stopCollectors() {
        restoreJob?.cancel()
        queueWatcherJob?.cancel()
        settlementWatcherJob?.cancel()
        restoreJob = null
        queueWatcherJob = null
        settlementWatcherJob = null
    }

    private data class RestoreStep(
        val notifyCurrent: Boolean,
        val shouldStop: Boolean,
    ) {
        companion object {
            val Ignore = RestoreStep(notifyCurrent = false, shouldStop = false)
            val Stop = RestoreStep(notifyCurrent = false, shouldStop = true)
        }
    }
}
