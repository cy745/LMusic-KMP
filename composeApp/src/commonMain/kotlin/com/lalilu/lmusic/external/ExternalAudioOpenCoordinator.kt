package com.lalilu.lmusic.external

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.extensions.GlobalToaster
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SnapshotCommitState
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.source.external.ExternalMediaMatch
import com.lalilu.lmedia.source.external.ExternalMediaMatcher
import com.lalilu.lmedia.source.sandbox.SandboxImportResult
import com.lalilu.lmedia.source.sandbox.SandboxMediaSource
import com.lalilu.lplayer.LPlayer
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single

/** Serializes external-open events from platform entry points through matching, import and playback. */
@Single
class ExternalAudioOpenCoordinator(
    private val platformMediaSource: PlatformMediaSource,
    private val audioRepository: AudioRepository,
    private val bindingRepository: MediaSourceBindingRepository,
) {
    private val logger = Logger.withTag("ExternalAudioOpen")
    private val scope = CoroutineScope(Dispatchers.io + SupervisorJob())
    private val mutex = Mutex()

    fun submit(file: PlatformFile) {
        scope.launch {
            mutex.withLock {
                val scopedAccess = file.startAccessingSecurityScopedResource()
                try {
                    notify("正在打开 ${file.name}")
                    openAndPlay(file)
                } catch (throwable: Throwable) {
                    logger.e(throwable) { "Failed to open external audio: ${file.name}" }
                    notify(throwable.message?.let { "打开音频失败：$it" } ?: "打开音频失败")
                } finally {
                    if (scopedAccess) file.stopAccessingSecurityScopedResource()
                }
            }
        }
    }

    private suspend fun openAndPlay(file: PlatformFile) {
        bindingRepository.startBinding()
        val persistedCandidates = audioRepository.getAudios().first()
        val sandbox = platformMediaSource.sources
            .filterIsInstance<SandboxMediaSource>()
            .singleOrNull()
            ?: error("Sandbox media source is unavailable")

        var existingMatch: ExternalMediaMatch? = null
        for (matcher in platformMediaSource.sources.filterIsInstance<ExternalMediaMatcher>()) {
            if (matcher is SandboxMediaSource) continue
            existingMatch = matcher.matchExternalMedia(file, persistedCandidates)
            if (existingMatch != null) break
        }

        val result = if (existingMatch != null) {
            logger.i { "Matched ${existingMatch.audio.id} by ${existingMatch.basis}" }
            existingMatch.audio
        } else {
            notify("正在导入 ${file.name}")
            when (val imported = sandbox.import(file, persistedCandidates)) {
                is SandboxImportResult.Existing -> imported.audio
                is SandboxImportResult.Imported -> awaitCommitted(
                    source = sandbox,
                    audio = imported.audio,
                    revision = imported.snapshotRevision,
                )
            }
        }

        val persisted = audioRepository.getAudio(result.id).first()
            ?: platformMediaSource.sources.firstOrNull { it.name == result.mediaSourceName }
                ?.snapshot?.value?.let { current ->
                    current.audios.firstOrNull { it.id == result.id }?.let {
                        awaitCommitted(
                            source = platformMediaSource.sources.first { source ->
                                source.name == result.mediaSourceName
                            },
                            audio = it,
                            revision = current.revision,
                        )
                    }
                }
            ?: error("Audio was not written to the media library")

        play(persisted)
        notify("正在播放：${persisted.title}")
    }

    private suspend fun awaitCommitted(
        source: MediaSource,
        audio: LAudio,
        revision: Long,
    ): LAudio {
        val terminal = withTimeout(COMMIT_TIMEOUT_MILLIS) {
            bindingRepository.observeSource(source.name)
                .filterNotNull()
                .first { status ->
                    when (val state = status.commitState) {
                        is SnapshotCommitState.Committed -> state.revision == revision
                        is SnapshotCommitState.Failed -> state.revision == revision
                        SnapshotCommitState.Idle,
                        is SnapshotCommitState.Committing -> false
                    }
                }
        }.commitState
        if (terminal is SnapshotCommitState.Failed) {
            error("写入媒体库失败：${terminal.message}")
        }
        return withTimeout(COMMIT_TIMEOUT_MILLIS) {
            audioRepository.getAudio(audio.id).filterNotNull().first()
        }
    }

    private suspend fun play(audio: LAudio) {
        val player = LPlayer.instance
        while (true) {
            val queue = player.queue.stateSnapshot()
            val existingIndex = queue.list.indexOfFirst { it.id == audio.id }
            val targetIndex = when {
                existingIndex >= 0 -> existingIndex
                queue.list.isEmpty() -> 0
                else -> (queue.index + 1).coerceIn(0, queue.list.size)
            }
            var applied = false
            player.queue.update(predicate = { it == queue }) {
                if (existingIndex < 0) {
                    if (queue.list.isEmpty()) addToEnd(listOf(audio)) else addToNext(listOf(audio))
                }
                switchTo(targetIndex)
                applied = true
            }
            if (!applied) continue
            player.skipTo(targetIndex, start = true)
            return
        }
    }

    private suspend fun notify(message: String) = withContext(Dispatchers.Main) {
        GlobalToaster?.show(message)
    }

    companion object {
        private const val COMMIT_TIMEOUT_MILLIS = 30_000L
    }
}
