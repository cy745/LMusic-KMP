package com.lalilu.lplayer.notifacation

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lalilu.lmedia.MediaCoverRequest
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.core.systemArtworkRequests
import com.lalilu.lplayer.core.systemMediaSnapshots
import com.lalilu.lplayer.playback.Playback
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import platform.CoreGraphics.*
import platform.MediaPlayer.*
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
object NowPlayingInfoNotification {
    private var scope: CoroutineScope? = null
    private val center = MPNowPlayingInfoCenter.defaultCenter()

    fun unbind() { scope?.cancel(); scope = null; center.nowPlayingInfo = null }

    fun bindPlayback(playback: Playback, sources: PlatformMediaSource? = null,
        artworkLoader: suspend (MediaCoverRequest) -> UIImage? = ::loadArtwork) {
        unbind()
        val binding = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = binding
        val info = mutableMapOf<String, Any>()
        var artworkKey: MediaKey? = null
        var artwork: MPMediaItemArtwork? = null
        fun publish() {
            if (scope === binding) center.nowPlayingInfo = info.toMap()
        }
        binding.launch {
            playback.systemMediaSnapshots().collect { snapshot ->
                val audio = snapshot.audio
                if (audio == null) { info.clear(); publish(); return@collect }
                info[MPMediaItemPropertyTitle] = audio.title
                info[MPMediaItemPropertyArtist] = audio.subtitle
                info[MPMediaItemPropertyPlaybackDuration] = snapshot.durationMs / 1000.0
                info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = snapshot.positionMs / 1000.0
                info[MPNowPlayingInfoPropertyPlaybackRate] = if (snapshot.playing) 1.0 else 0.0
                if (artworkKey == audio.mediaKey && artwork != null) info[MPMediaItemPropertyArtwork] = artwork!!
                else info.remove(MPMediaItemPropertyArtwork)
                publish()
            }
        }
        binding.launch {
            playback.systemArtworkRequests(sources).collectLatest { request ->
                artworkKey = null; artwork = null; info.remove(MPMediaItemPropertyArtwork); publish()
                if (request == null) return@collectLatest
                try {
                    val image = artworkLoader(request)
                    ensureActive()
                    if (scope !== binding || playback.queue.currentItem()?.mediaKey != request.audio.mediaKey) return@collectLatest
                    artworkKey = request.audio.mediaKey
                    artwork = image?.let { MPMediaItemArtwork(boundsSize = it.size) { _ -> it } }
                    artwork?.let { info[MPMediaItemPropertyArtwork] = it }
                    publish()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { co.touchlab.kermit.Logger.w("System artwork load failed", error) }
            }
        }
    }

    private suspend fun loadArtwork(request: MediaCoverRequest): UIImage? {
        val result = SingletonImageLoader.get(PlatformContext.INSTANCE).execute(
            ImageRequest.Builder(PlatformContext.INSTANCE).data(request).size(256, 256).build())
        val bitmap = result.image?.toBitmap() ?: return null
        val bytes = bitmap.readPixels() ?: return null
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        try {
            return bytes.usePinned { pinned ->
                val context = CGBitmapContextCreate(pinned.addressOf(0), bitmap.width.toULong(), bitmap.height.toULong(),
                    8u, (4 * bitmap.width).toULong(), colorSpace,
                    CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little) ?: return@usePinned null
                try {
                    val cgImage = CGBitmapContextCreateImage(context) ?: return@usePinned null
                    try { UIImage.imageWithCGImage(cgImage) } finally { CGImageRelease(cgImage) }
                } finally { CGContextRelease(context) }
            }
        } finally { CGColorSpaceRelease(colorSpace) }
    }
}
