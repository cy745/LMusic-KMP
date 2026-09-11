package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

internal const val LEGACY_HISTORY_WAIT_MILLIS = 15_000L

/**
 * 已知来源只等待目标源；旧历史没有来源信息时，最多等待 15 秒后允许回退。
 *
 * 这里只用于决定历史当前歌曲是否还能在本轮启动中出现，不参与普通播放，也不会要求所有来源成功：
 * 未配置、权限拒绝、扫描失败和写入失败都属于已经得到结果，可以让恢复器停止等待旧 current。
 */
internal fun MediaSourceBindingRepository.observeHistoryRestoreSettled(
    sourceName: String? = null,
): Flow<Boolean> {
    val sourceFlows = getSources().sources.filter { sourceName == null || it.name == sourceName }.map { source ->
        combine(source.contentState, observeSource(source.name)) { content, status ->
            if (status?.enabled == false && !status.enablementChanging) return@combine true
            val commitState = status?.commitState ?: SnapshotCommitState.Idle
            if (commitState is SnapshotCommitState.Committing) {
                return@combine false
            }

            when (content.availability) {
                MediaContentAvailability.Uninitialized,
                MediaContentAvailability.Preparing -> false

                is MediaContentAvailability.Unavailable -> true
                MediaContentAvailability.Ready -> {
                    val snapshotRevision = source.snapshot.value?.revision ?: return@combine false
                    val terminalRevision = when (commitState) {
                        is SnapshotCommitState.Committed -> commitState.revision
                        is SnapshotCommitState.Failed -> commitState.revision
                        SnapshotCommitState.Idle,
                        is SnapshotCommitState.Committing -> null
                    }
                    terminalRevision == snapshotRevision && status?.resultRevision == snapshotRevision
                }
            }
        }.distinctUntilChanged()
    }

    if (sourceFlows.isEmpty()) return flowOf(true)
    val settled = combine(sourceFlows) { values -> values.all { it } }
        .distinctUntilChanged()
    if (sourceName != null) return settled
    // This only releases the historical-current wait. The restorer continues observing missing
    // identities, and no database availability flags or source tasks are changed by this timer.
    return combine(settled, flow {
        emit(false)
        delay(LEGACY_HISTORY_WAIT_MILLIS)
        emit(true)
    }) { allSettled, expired -> allSettled || expired }.distinctUntilChanged()
}
