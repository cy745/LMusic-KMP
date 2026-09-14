package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.resolveMediaData
import com.lalilu.lplayer.notification.BrowserMediaSessionHelper
import com.lalilu.lplayer.playback.PlaybackEngine
import io.github.vinceglb.filekit.utils.toJsArray
import com.lalilu.lplayer.action.launchPlayerAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.w3c.dom.Audio
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag


@Single(binds = [Playback::class])
@OptIn(ExperimentalWasmJsInterop::class)
class AudioPlayback(
    audioRepository: AudioRepository,
    private val history: PlaybackHistory
) : AbstractPlayback(history = history, audioRepository = audioRepository), KoinComponent {
    companion object {
        const val TAG = "AudioPlayback"
    }

    override val platformMediaSource: PlatformMediaSource by inject()
    override fun createEngines(): List<PlaybackEngine> = emptyList()
    private val player = Audio()
    private var loadedMediaKey: MediaKey? = null

    init {
        BrowserMediaSessionHelper.bindPlayback(this)
        player.addEventListener("ended") {
            launchPlayerAction { skipToNext() }
        }
        startHistoryPlayback()
    }

    override suspend fun restoreLoadedItem(audio: LAudio, position: Long, start: Boolean) {
        playItem(audio, false)
        withTimeout(30_000) {
            while (player.readyState.toInt() < 1) {
                check(player.error == null) { "History media load failed" }
                delay(50)
            }
        }
        player.currentTime = position.coerceAtLeast(0) / 1000.0
        if (start) player.play()
    }

    private suspend fun playItem(item: LAudio, start: Boolean) {
        player.pause()
        loadedMediaKey = null

        when (val data = platformMediaSource.resolveMediaData(item)) {
            is MediaData.Url -> {
                Logger.i(tag = TAG, messageString = "prepared with url: ${data.url}")
                player.src = data.url
                player.load()
            }

            is MediaData.Bytes -> {
                Logger.i(tag = TAG, messageString = "prepared with bytes: ${data.bytes.size}")
                val blob = data.bytes.toJsBlob()
                val url = URL.createObjectURL(blob)
                player.src = url
                player.load()
            }

        }

        loadedMediaKey = item.mediaKey
        if (start) {
            player.play()
            _isPlaying.value = true
        }
    }


    override suspend fun play() {
        try {
            if (loadedMediaKey != null && loadedMediaKey == queue.currentItem()?.mediaKey) {
                player.play()
                _isPlaying.value = true
            } else {
                val current = queue.currentItem()
                    ?: throw Exception("No media to play")

                playItem(current, true)
            }
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    override suspend fun pause() {
        try {
            player.pause()
            _isPlaying.value = false
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    override suspend fun togglePlayPause() {
        if (!player.paused) pause() else play()
    }

    override suspend fun stop() {
        try {
            player.pause()
            _isPlaying.value = false
            loadedMediaKey = null
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    override suspend fun skipTo(index: Int, start: Boolean) {
        try {
            val state = queue.stateSnapshot()
            val targetItem = state.list.getOrNull(index)
                ?: throw Exception("Invalid index")
            if (loadedMediaKey == targetItem.mediaKey) {
                seekTo(0)
                queue.update { switchTo(index) }
                prepareSurpriseNext()
                if (start) play() else pause()
            } else {
                playItem(targetItem, start)
                queue.update { switchTo(index) }
                prepareSurpriseNext()
            }
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            player.currentTime = positionMs.coerceAtLeast(0L) / 1000.0
        } catch (e: Exception) {
            reportPlaybackCommandFailure(e) {
                Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
                emitError(e)
            }
        }
    }

    override fun currentPosition(): Long {
        return (player.currentTime * 1000).toLong()
    }
}

// 将 Kotlin ByteArray 转换为 JS Blob
@OptIn(ExperimentalWasmJsInterop::class)
fun ByteArray.toJsBlob(mimeType: String = "application/octet-stream"): Blob {
    return Blob(
        blobParts = this.toJsArray(),
        options = BlobPropertyBag(type = mimeType)
    )
}
