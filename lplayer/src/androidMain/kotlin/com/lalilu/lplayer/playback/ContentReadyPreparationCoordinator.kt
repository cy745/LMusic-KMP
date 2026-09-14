package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaContentUnavailableException
import com.lalilu.lmedia.domain.source.awaitContentReadyOrThrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 保存一份可取消的“内容就绪后准备播放”请求。
 *
 * Media3 的 DataSource 仍保留有限等待作为最后保护；正常路径由这里在普通协程中等待目标来源，
 * current 变化会使旧请求失效，播放/暂停则只修改请求最终应用时的 playWhenReady。
 */
internal class ContentReadyPreparationCoordinator(
    private val scope: CoroutineScope,
    private val sourceOf: (LAudio) -> MediaSource?,
    private val preparationTicket: () -> Long,
    private val onReady: suspend (audio: LAudio, latestIntent: () -> Pair<Boolean, Long>?) -> Unit,
    private val onSourceMissing: suspend (LAudio) -> Unit,
) {
    private val lock = Any()
    private var job: Job? = null
    private var generation = 0L
    private var audioId: String? = null
    private var playWhenReady = false
    private var controlTicket = 0L

    fun request(audio: LAudio, playWhenReady: Boolean) {
        val ticket = preparationTicket()
        val source = sourceOf(audio)
        if (source == null) {
            cancel()
            scope.launch { onSourceMissing(audio) }
            return
        }

        val requestGeneration = synchronized(lock) {
            job?.cancel()
            generation += 1
            audioId = audio.playbackId
            this.playWhenReady = playWhenReady
            controlTicket = ticket
            generation
        }

        val requestJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                source.awaitContentReadyOrThrow()
                // Read at native application time, not before dispatching to the main thread.
                onReady(audio) {
                    synchronized(lock) {
                        if (requestGeneration != generation || audioId != audio.playbackId) null
                        else this@ContentReadyPreparationCoordinator.playWhenReady to controlTicket
                    }
                }
            } catch (_: MediaContentUnavailableException) {
                val isCurrent = synchronized(lock) {
                    requestGeneration == generation && audioId == audio.playbackId
                }
                if (isCurrent) onSourceMissing(audio)
            } finally {
                synchronized(lock) {
                    if (requestGeneration == generation) {
                        job = null
                        audioId = null
                    }
                }
            }
        }

        val shouldStart = synchronized(lock) {
            if (requestGeneration == generation) {
                job = requestJob
                true
            } else {
                false
            }
        }
        if (shouldStart) requestJob.start() else requestJob.cancel()
    }

    fun updatePlayIntent(audioId: String?, playWhenReady: Boolean): Boolean = synchronized(lock) {
        if (audioId == null || this.audioId != audioId || job?.isActive != true) return@synchronized false
        this.playWhenReady = playWhenReady
        if (playWhenReady) controlTicket = preparationTicket()
        true
    }

    fun hasPendingPlayIntent(): Boolean = synchronized(lock) {
        job?.isActive == true && playWhenReady
    }

    fun cancelIfCurrentChanged(audioId: String?) {
        synchronized(lock) {
            if (this.audioId == null || this.audioId == audioId) return
            cancelLocked()
        }
    }

    fun cancel() {
        synchronized(lock) { cancelLocked() }
    }

    private fun cancelLocked() {
        generation += 1
        job?.cancel()
        job = null
        audioId = null
        playWhenReady = false
    }
}
