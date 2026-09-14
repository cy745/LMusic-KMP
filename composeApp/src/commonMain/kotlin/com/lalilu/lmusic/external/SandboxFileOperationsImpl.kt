package com.lalilu.lmusic.external

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.source.sandbox.SandboxFileOperations
import com.lalilu.lmedia.source.sandbox.SandboxMediaSource
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.playback.QueueRemovalRecovery
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single

@Single(binds = [SandboxFileOperations::class])
class SandboxFileOperationsImpl(
    private val audioRepository: AudioRepository,
    private val bindingRepository: MediaSourceBindingRepository,
) : SandboxFileOperations {
    override suspend fun rename(source: SandboxMediaSource, audio: LAudio, newBaseName: String): LAudio {
        return bindingRepository.withEnabledSource(source.name) {
            source.rename(audio, newBaseName) { snapshot ->
                val target = snapshot.audios.single { it.mediaKey == audio.mediaKey }
                val path = requireNotNull(target.extra?.get(SandboxMediaSource.EXTRA_PATH))
                awaitExternalAudioCommit(
                    expected = target,
                    revision = snapshot.revision,
                    statuses = bindingRepository.observeSource(source.name),
                    persisted = audioRepository.getAudioByPlaybackId(target.playbackId),
                    expectedPath = path,
                )
                withTimeout(30_000) {
                    LPlayer.instance.queue.expandedItems.first { state ->
                        state.list.filter { it.mediaKey == target.mediaKey }
                            .all { it.extra?.get(SandboxMediaSource.EXTRA_PATH) == path }
                    }
                }
            }
        }
    }

    override suspend fun delete(source: SandboxMediaSource, audio: LAudio) {
        bindingRepository.withEnabledSource(source.name) {
            val queueRemoval = QueueRemovalRecovery(LPlayer.instance, setOf(audio.mediaKey))
            source.delete(audio) { snapshot ->
                val restored = snapshot.audios.any { it.mediaKey == audio.mediaKey }
                withTimeout(30_000) {
                    combine(bindingRepository.observeSource(source.name), audioRepository.getAudioByPlaybackId(audio.playbackId)) { status, row ->
                        when (val commit = status?.commitState) {
                            is SnapshotCommitState.Failed -> {
                                if (commit.revision >= snapshot.revision) error("写入媒体库失败：${commit.message}")
                                false
                            }
                            is SnapshotCommitState.Committed -> {
                                val available = row?.mediaKey == audio.mediaKey && row.available
                                commit.revision >= snapshot.revision && available == restored
                            }
                            else -> false
                        }
                    }.first { it }
                }
                if (!restored) {
                    queueRemoval.remove()
                } else {
                    queueRemoval.restore(snapshot.audios)
                }
            }
        }
    }
}
