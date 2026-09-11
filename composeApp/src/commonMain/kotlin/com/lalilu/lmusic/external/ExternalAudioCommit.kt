package com.lalilu.lmusic.external

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.repository.SourceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** A newer snapshot may supersede the imported revision, but cannot substitute another song. */
internal suspend fun awaitExternalAudioCommit(
    expected: LAudio,
    revision: Long,
    statuses: Flow<SourceStatus?>,
    persisted: Flow<LAudio?>,
    timeoutMillis: Long = 30_000L,
    expectedPath: String? = null,
): LAudio = withTimeout(timeoutMillis) {
    combine(statuses, persisted) { status, audio ->
        if (status == null) return@combine null
        check(status.enabled && !status.enablementChanging) { "数据源已停用或正在切换状态" }
        when (val commit = status.commitState) {
            is SnapshotCommitState.Failed -> {
                if (commit.revision >= revision) error("写入媒体库失败：${commit.message}")
                null
            }
            is SnapshotCommitState.Committed -> audio?.takeIf {
                commit.revision >= revision && it.id == expected.id &&
                    it.mediaSourceName == expected.mediaSourceName && it.available &&
                    (expectedPath == null || it.extra?.get("path") == expectedPath)
            }
            else -> null
        }
    }.filterNotNull().first()
}
