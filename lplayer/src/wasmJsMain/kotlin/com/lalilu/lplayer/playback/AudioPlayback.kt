package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.source.Library
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.util.flatten
import com.lalilu.lplayer.notification.BrowserMediaSessionHelper
import io.github.vinceglb.filekit.utils.toJsArray
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.w3c.dom.Audio
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

class AudioPlayback(
    private val library: Library
) : AbstractPlayback(), KoinComponent {
    companion object {
        const val TAG = "AudioPlayback"
    }

    private val platformMediaSource: PlatformMediaSource by inject()
    private val player = Audio()

    init {
        BrowserMediaSessionHelper.bindPlayback(this)
        player.addEventListener("ended") {
            launch { skipToNext() }
        }
    }

    override suspend fun playItem(item: LAudio) {
        val source = platformMediaSource.sources
            .firstOrNull { item.mediaSourceName == it.name }
            ?: throw Exception("No source item found for ${item.mediaSourceName}")

        player.pause()

        val data = source.dataSource.getMedia(item)
        when (data) {
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

            else -> {
                throw Exception("Unsupported data type: data: $data")
            }
        }

        player.play()
        _isPlaying.value = true
        _currentItemIndex.value = _playlist.value.flatten().indexOf(item)
        updateNavigationCapabilities()
    }


    override suspend fun play() {
        try {
            if (player.duration > 0) {
                player.play()
                _isPlaying.value = true
            } else {
                val current = currentItem.value
                    ?: throw Exception("No media to play")

                playItem(current)
            }
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun pause() {
        try {
            player.pause()
            _isPlaying.value = false
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun togglePlayPause() {
        if (!player.paused) pause() else play()
    }

    override suspend fun stop() {
        try {
            player.pause()
            _isPlaying.value = false
            _currentItemIndex.value = 0
            updateNavigationCapabilities()
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun skipTo(index: Int) {
        try {
            val targetItem = _playlist.value.flatten().getOrNull(index)
                ?: throw Exception("Invalid index")

            if (targetItem.id == currentItem.value?.id) {
                seekTo(0)
            } else {
                playItem(targetItem)
            }
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            player.fastSeek(positionMs.toDouble())
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override fun currentPosition(): Long {
        return (player.currentTime * 1000).toLong()
    }
}

// 将 Kotlin ByteArray 转换为 JS Blob
fun ByteArray.toJsBlob(mimeType: String = "application/octet-stream"): Blob {
    return Blob(
        blobParts = this.toJsArray(),
        options = BlobPropertyBag(type = mimeType)
    )
}