package com.lalilu.lmusic.external

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.extensions.GlobalToaster
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
                } catch (timeout: TimeoutCancellationException) {
                    logger.e(timeout) { "Timed out opening external audio: ${file.name}" }
                    notify("打开音频超时，请稍后重试")
                } catch (cancelled: CancellationException) {
                    throw cancelled
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
        var existingMatch: ExternalMediaMatch? = null
        for (matcher in platformMediaSource.enabledSources.filterIsInstance<ExternalMediaMatcher>()) {
            if (matcher is SandboxMediaSource) continue
            existingMatch = matcher.matchExternalMedia(file, persistedCandidates)
            if (existingMatch != null) break
        }

        val matched = existingMatch
        if (matched != null) {
            logger.i { "Matched ${matched.audio.id} by ${matched.basis}" }
            bindingRepository.withEnabledSource(matched.audio.mediaSourceName) {
                finishOpening(matched.audio)
            }
            return
        }
        val sandbox = platformMediaSource.sources
            .filterIsInstance<SandboxMediaSource>()
            .singleOrNull()
            ?: error("Sandbox media source is unavailable")
        bindingRepository.withEnabledSource(sandbox.name) {
            notify("正在导入 ${file.name}")
            val result = when (val imported = sandbox.import(file, persistedCandidates)) {
                is SandboxImportResult.Existing -> imported.audio
                is SandboxImportResult.Imported -> awaitCommitted(
                    source = sandbox,
                    audio = imported.audio,
                    revision = imported.snapshotRevision,
                )
            }
            finishOpening(result)
        }
    }

    private suspend fun finishOpening(result: LAudio) {
        val persisted = audioRepository.getAudio(result.id).first()
            ?.takeIf { it.mediaSourceName == result.mediaSourceName && it.available }
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
    ): LAudio = awaitExternalAudioCommit(
        expected = audio,
        revision = revision,
        statuses = bindingRepository.observeSource(source.name),
        persisted = audioRepository.getAudio(audio.id),
    )

    private suspend fun play(audio: LAudio) {
        LPlayer.instance.playAudio(audio)
    }

    private suspend fun notify(message: String) = withContext(Dispatchers.Main) {
        GlobalToaster?.show(message)
    }

}
