package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.SourceItemDefaults
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.util.flatten
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalForeignApi::class)
class AVPlayerPlayback : Playback, CoroutineScope, KoinComponent {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val platformMediaSource: PlatformMediaSource by inject()
    private var prepareJob: Job? = null
    private val player: AVPlayer = AVPlayer()


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

    private fun playWithItem(item: LAudio) {
        prepareJob?.cancel()
        prepareJob = launch {
            val source = platformMediaSource.sources
                .firstOrNull { item.mediaSourceName == it.name }
                ?: throw Exception("No source item found for ${item.mediaSourceName}")

            item.sourceItem = SourceItemDefaults.RequestUrl

            val data = source.dataSource.getMedia(item)
            val playerItem = when (data) {
                is MediaData.Url -> {
                    Logger.i(tag = "AVPlayer", messageString = "prepared with url: ${data.url}")
                    val url = NSURL.URLWithString(data.url)!!
                    AVPlayerItem(url)
                }

                else -> {
                    throw Exception("Unsupported source item: $data")
                }
            }

            player.replaceCurrentItemWithPlayerItem(playerItem)
            player.play()
            currentItemIndex.value = flattenPlaylist.value.indexOf(item)

//            when (val source = item.sourceItem) {
//                is SourceItem.MPItem -> {
//                    val assetUrl = source.item.assetURL
//                    if (assetUrl != null) {
//                        val asset = AVAsset.assetWithURL(assetUrl)
//                        val playerItem = AVPlayerItem(asset)
//                        player.replaceCurrentItemWithPlayerItem(playerItem)
//                        player.play()
//                        currentItemIndex.value = flattenPlaylist.value.indexOf(item)
//                    } else {
//                        throw Exception("Asset URL is null")
//                    }
//                }
//
//                is SourceItem.MusicKitItem -> {
//                    Logger.i("${source.item}")
//                    val assetUrl = source.item.url()
//                    if (assetUrl != null) {
//                        val asset = AVAsset.assetWithURL(assetUrl)
//                        val playerItem = AVPlayerItem(asset)
//                        player.replaceCurrentItemWithPlayerItem(playerItem)
//                        player.play()
//                        currentItemIndex.value = flattenPlaylist.value.indexOf(item)
//                    } else {
//                        throw Exception("Asset URL is null")
//                    }
//                }
//
//                else -> {
//                    throw Exception("Unsupported source item: ${item.sourceItem}")
//                }
//            }
        }
    }

    override fun play() = runWith {
        if (player.currentItem != null) {
            player.play()
            isPlayingFlow.value = true
        } else {
            val current = currentItemFlow.value
                ?: throw Exception("No media to play")

            playWithItem(current)
        }
    }

    override fun pause() = runWith {
        player.pause()
        isPlayingFlow.value = false
    }

    override fun togglePlayPause() = runWith {
        if (isPlayingFlow.value) {
            pause()
        } else {
            play()
        }
    }

    override fun stop() = runWith {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        isPlayingFlow.value = false
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
        val time = CMTimeMake(value = positionMs, timescale = 1000)
        player.seekToTime(time)
    }

    override fun flattenPlaylist(): StateFlow<List<LAudio>> = flattenPlaylist
    override fun playlist(): StateFlow<List<LItem>> = playlist

    override fun updatePlaylist(playlist: List<LItem>) {
        launch { this@AVPlayerPlayback.playlist.emit(playlist) }
    }

    override fun clearPlaylist() {
        launch { this@AVPlayerPlayback.playlist.emit(emptyList()) }
    }

    override fun isPlaying(): StateFlow<Boolean> = isPlayingFlow

    override fun currentItem(): StateFlow<LAudio?> = currentItemFlow

    override fun currentItemIndex(): StateFlow<Int> = currentItemIndex

    override fun currentPlaybackState(): StateFlow<PlaybackState> = currentPlaybackState

    @OptIn(ExperimentalForeignApi::class)
    override fun currentDuration(): Long = runWith(0L) {
        memScoped { player.currentItem()?.duration()?.getPointer(this)[0]?.value ?: 0L }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun currentPosition(): Long = runWith(0L) {
        memScoped { player.currentTime().getPointer(this)[0].value }
    }

    override fun currentBufferedPosition(): Long = runWith(0L) {
        // iOS AVPlayer doesn't expose buffered position directly in the same way
        // We'll return the duration as an approximation for now
        currentDuration()
    }

    override fun errorMessage(): SharedFlow<Throwable> = errorSharedFlow


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