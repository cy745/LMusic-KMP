package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * 判断所有已注册数据源的首轮内容准备和数据库提交是否都已经得到结果。
 *
 * 这里只用于决定历史当前歌曲是否还能在本轮启动中出现，不参与普通播放，也不会要求所有来源成功：
 * 未配置、权限拒绝、扫描失败和写入失败都属于已经得到结果，可以让恢复器停止等待旧 current。
 */
internal fun MediaSourceBindingRepository.observeHistoryRestoreSettled(): Flow<Boolean> {
    val sourceFlows = getSources().sources.map { source ->
        combine(source.contentState, observeSource(source.name)) { content, status ->
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
    return combine(sourceFlows) { settled -> settled.all { it } }
        .distinctUntilChanged()
}
