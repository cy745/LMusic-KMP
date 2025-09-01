package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.util.flatten
import com.lalilu.lplayer.notification.BrowserMediaSessionHelper
import io.github.vinceglb.filekit.utils.toJsArray
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.w3c.dom.Audio
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.coroutines.CoroutineContext

class AudioPlayback : Playback, CoroutineScope, KoinComponent {
    companion object {
        const val TAG = "AudioPlayback"
    }

    override val coroutineContext: CoroutineContext = Dispatchers.io + SupervisorJob()
    private val platformMediaSource: PlatformMediaSource by inject()
    private var prepareJob: Job? = null

    private val errorSharedFlow = MutableSharedFlow<Throwable>()
    private val isPlayingFlow = MutableStateFlow(false)
    private val playlist = MutableStateFlow<List<LItem>>(emptyList())
    private val flattenPlaylist = playlist.flatten()
        .stateIn(this, SharingStarted.WhileSubscribed(), emptyList())

    private val currentPlaybackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val currentItemIndex = MutableStateFlow(0)
    private val currentItemFlow = flattenPlaylist
        .combine(currentItemIndex) { list, index -> list.getOrNull(index) }
        .stateIn(this, SharingStarted.WhileSubscribed(), null)

    private val player = Audio()

    init {
        BrowserMediaSessionHelper.bindPlayback(this)
        player.addEventListener("ended") {
            skipToNext()
        }
    }

    private fun playWithItem(item: LAudio) {
        prepareJob?.cancel()
        prepareJob = launch {
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
            isPlayingFlow.value = true
            currentItemIndex.value = flattenPlaylist.value.indexOf(item)
        }
    }


    override fun play() = runWith {
        if (player.duration > 0) {
            player.play()
        } else {
            val current = currentItemFlow.value
                ?: throw Exception("No media to play")

            playWithItem(current)
        }
    }

    override fun pause() = runWith {
        player.pause()
    }

    override fun togglePlayPause() = runWith {
        if (!player.paused) pause() else play()
    }

    override fun stop() = runWith {
        player.pause()
    }

    override fun skipTo(index: Int) = runWith {
        val targetItem = flattenPlaylist.value.getOrNull(index)
            ?: throw Exception("Invalid index")

        if (targetItem.id == currentItemFlow.value?.id) {
            seekTo(0)
        } else {
            playWithItem(targetItem)
        }
    }

    override fun skipToNext() = runWith {
        val nextIndex = (currentItemIndex.value + 1) % flattenPlaylist.value.size
        val nextItem = flattenPlaylist.value.getOrNull(nextIndex)
            ?: throw Exception("No next item")

        playWithItem(nextItem)
    }

    override fun skipTpPrevious() = runWith {
        val previousIndex = (currentItemIndex.value - 1 + flattenPlaylist.value.size) % flattenPlaylist.value.size
        val previousItem = flattenPlaylist.value.getOrNull(previousIndex)
            ?: throw Exception("No previous item")

        playWithItem(previousItem)
    }

    override fun seekTo(positionMs: Long) = runWith {
        player.fastSeek(positionMs.toDouble())
    }

    override fun flattenPlaylist(): StateFlow<List<LAudio>> = flattenPlaylist
    override fun playlist(): StateFlow<List<LItem>> = playlist
    override fun updatePlaylist(playlist: List<LItem>) {
        launch { this@AudioPlayback.playlist.emit(playlist) }
    }

    override fun clearPlaylist() {
        launch { this@AudioPlayback.playlist.emit(emptyList()) }
    }

    override fun isPlaying(): StateFlow<Boolean> = isPlayingFlow
    override fun currentItem(): StateFlow<LAudio?> = currentItemFlow
    override fun currentItemIndex(): StateFlow<Int> = currentItemIndex
    override fun currentPlaybackState(): StateFlow<PlaybackState> = currentPlaybackState

    override fun currentDuration(): Long = runWith(0L) {
        player.duration.toLong()
    }

    override fun currentPosition(): Long = runWith(0L) {
        player.currentTime.toLong()
    }

    override fun currentBufferedPosition(): Long = runWith(0L) {
        player.buffered.length.toLong()
    }

    override fun errorMessage(): SharedFlow<Throwable> {
        return errorSharedFlow
    }

    private fun runWith(callback: () -> Unit) {
        try {
            callback()
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            launch { errorSharedFlow.emit(e) }
        }
    }

    private fun <T> runWith(default: T, callback: () -> T): T {
        return try {
            callback()
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            launch { errorSharedFlow.emit(e) }
            default
        }
    }
}

// 将 Kotlin ByteArray 转换为 JS Blob
fun ByteArray.toJsBlob(mimeType: String = "application/octet-stream"): Blob {
    return Blob(
        blobParts = this.toJsArray(),
        options = BlobPropertyBag(type = mimeType)
    )
}