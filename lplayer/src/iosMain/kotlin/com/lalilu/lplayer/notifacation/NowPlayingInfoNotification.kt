package com.lalilu.lplayer.notifacation

import co.touchlab.kermit.Logger
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lalilu.common.ext.io
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.playback.Playback
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreGraphics.*
import platform.Foundation.NSOperationQueue
import platform.MediaPlayer.*
import platform.UIKit.UIImage
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
object NowPlayingInfoNotification : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val logger = Logger.withTag("NowPlayingInfoNotification")
    private val nowPlayingInfoCenter = MPNowPlayingInfoCenter.defaultCenter()
    private val nowPlayingInfo = mutableMapOf<String, Any>()
    private val updateMutex = Mutex()

    fun debugLog(message: String) = logger.i(messageString = message)

    fun bindPlayback(playback: Playback) {
        debugLog("Binding playback to NowPlayingInfoNotification")
        playback.queue.currentItemFlow()
            .onEach { track ->
                debugLog("Received new track: ${track?.title} by ${track?.subtitle}")
                nowPlayingInfo.apply {
                    this[MPMediaItemPropertyTitle] = track?.title ?: ""
                    this[MPMediaItemPropertyArtist] = track?.subtitle ?: ""
                    this[MPMediaItemPropertyAlbumTitle] = track?.subtitle ?: ""
                    this[MPMediaItemPropertyPlaybackDuration] = playback.currentDuration.value.toDouble().div(1000)
                    this[MPNowPlayingInfoPropertyPlaybackRate] = if (playback.isPlaying.value) 1.0 else 0.0
                    this[MPNowPlayingInfoPropertyElapsedPlaybackTime] = 0.0
                }

                val placeholderImage = UIImage.imageNamed("AppIcon")
                debugLog("Placeholder image loaded: ${placeholderImage != null}")
                UIImage.imageNamed("AppIcon")?.let { placeholderImage ->
                    nowPlayingInfo[MPMediaItemPropertyArtwork] =
                        MPMediaItemArtwork(boundsSize = placeholderImage.size) { _ -> placeholderImage }
                }

                debugLog("Updating now playing info with track info")
                updateNowPlayingInfo()

                debugLog("Loading album artwork for track")
                loadAlbumArtwork(track)
            }.launchIn(this)

        playback.isPlaying
            .combine(playback.currentDuration) { isPlaying, duration -> isPlaying to duration }
            .onEach { (isPlaying, duration) ->
                debugLog("Playback state changed, isPlaying: $isPlaying")
                nowPlayingInfo.apply {
                    this[MPMediaItemPropertyPlaybackDuration] = duration.toDouble().div(1000)
                    this[MPNowPlayingInfoPropertyPlaybackRate] = if (isPlaying) 1.0 else 0.0
                    this[MPNowPlayingInfoPropertyElapsedPlaybackTime] = playback.currentPosition().toDouble()
                        .div(1000)
                }
                debugLog(
                    "Playback info - Duration: ${nowPlayingInfo[MPMediaItemPropertyPlaybackDuration]}, " +
                            "Rate: ${nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate]}, " +
                            "Elapsed: ${nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime]}"
                )
                updateNowPlayingInfo()
            }.launchIn(this)
    }

    private suspend fun loadAlbumArtwork(track: LAudio?) = withContext(Dispatchers.io) {
        debugLog("Start loading album artwork for track: ${track?.title}")
        val imageLoader = SingletonImageLoader.get(PlatformContext.INSTANCE)
        val imageResult = track?.let {
            imageLoader.execute(
                ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data(track)
                    .build()
            )
        }
        val bitmap = imageResult?.image?.toBitmap()
        val bytes = bitmap?.readPixels()

        debugLog("bitmap bytes loaded: ${bytes != null}, size: ${bytes?.size}")
        if (bitmap == null || bytes == null) {
            debugLog("No album artwork data found, removing artwork from now playing info")
            nowPlayingInfo.remove(MPMediaItemPropertyArtwork)
            updateNowPlayingInfo()
            return@withContext
        }

        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            data = bytes.refTo(0),
            width = bitmap.width.toULong(),
            height = bitmap.height.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = (4 * bitmap.width).toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or
                    kCGBitmapByteOrder32Little
        )

        val uiImage = CGBitmapContextCreateImage(context)
            ?.let { UIImage.imageWithCGImage(it) }

        debugLog("Album artwork data loaded, creating UIImage: ${uiImage != null}")
        uiImage?.let { image ->
            nowPlayingInfo[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
            debugLog("Album artwork updated, refreshing now playing info")
            updateNowPlayingInfo()
        }
    }

    /**
     * 刷新当前播放信息
     */
    private suspend fun updateNowPlayingInfo() {
        updateMutex.withLock {
            suspendCancellableCoroutine { continuation ->
                NSOperationQueue.mainQueue().addOperationWithBlock {
                    nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo.toMap()
                    continuation.resume(true)
                }
            }
        }
    }
}