package com.lalilu.lplayer.player

import co.touchlab.kermit.Logger
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.lalilu.cinterop.ObserverProtocol
import com.lalilu.common.ext.io
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.SourceItemDefaults
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.util.flatten
import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.PlaybackState
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.*
import platform.MediaPlayer.*
import platform.UIKit.UIImage
import platform.darwin.NSEC_PER_SEC
import platform.darwin.NSObject
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class)
class WrappedAVPlayer : Playback, CoroutineScope, KoinComponent {
    companion object {
        const val TAG = "WrappedAVPlayer"
    }

    fun debugLog(message: String) {
        Logger.i(tag = TAG, messageString = message)
    }

    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val platformMediaSource: PlatformMediaSource by inject()
    private var prepareJob: Job? = null

    private val nowPlayingInfoCenter = MPNowPlayingInfoCenter.defaultCenter()
    private val remoteCommandCenter = MPRemoteCommandCenter.sharedCommandCenter()
    private var notificationObserver: Any? = null
    private var timeObserver: Any? = null
    val player: AVPlayer = AVPlayer()
    val nowPlayingInfo = mutableMapOf<String, Any>()

    init {
        setUpAudioSession()
        setupRemoteCommands()
    }

    private val observer: (CValue<CMTime>) -> Unit = { time ->
        val seconds = CMTimeGetSeconds(time)
        debugLog("observer: ${seconds}")
        if (player.currentItem?.isPlaybackLikelyToKeepUp() == true) {
            debugLog("setupRemoteCommands: ${seconds}")
            setupRemoteCommands()
        }
//        if (player.currentItem?.isPlaybackLikelyToKeepUp() == true) {
//            listener?.onBufferingStateChanged(false)
//            listener?.onPlaybackStateChanged(isPlaying())
//            setupRemoteCommands()
//            currentTrack?.let { updateNowPlayingInfo(it) }
//        } else {
//            listener?.onBufferingStateChanged(true)
//        }
    }

    private fun updateNowPlayingInfo(track: LAudio) = launch {
        nowPlayingInfo.apply {
            this[MPMediaItemPropertyTitle] = track.title
            this[MPMediaItemPropertyArtist] = track.subtitle
            this[MPMediaItemPropertyAlbumTitle] = track.subtitle
            this[MPMediaItemPropertyPlaybackDuration] = currentDuration().toDouble().div(1000)
            this[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentPosition().toDouble().div(1000)
            this[MPNowPlayingInfoPropertyPlaybackRate] = if (isPlayingFlow.value) 1.0 else 0.0
        }

        UIImage.imageNamed("AppIcon")?.let { placeholderImage ->
            nowPlayingInfo[MPMediaItemPropertyArtwork] =
                MPMediaItemArtwork(boundsSize = placeholderImage.size) { _ -> placeholderImage }
        }

        nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo.toMap()

//        loadAlbumArtwork(track)
    }

    private suspend fun loadAlbumArtwork(track: LAudio?) = withContext(Dispatchers.io) {
        val imageLoader = SingletonImageLoader.get(PlatformContext.INSTANCE)
        val imageResult = track?.let {
            imageLoader.execute(
                ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data(track)
                    .build()
            )
        }
        val bitmap = imageResult?.image?.toBitmap()
        val pixels = bitmap?.peekPixels()?.buffer

        val nsData = pixels
            ?.takeIf { pixels.size > 0 }
            ?.let {
                val address = pixels.writableData().toLong()
                val pointer = address.toCPointer<CPointed>()

                NSData.dataWithBytes(pointer, it.size.toULong())
            }

        if (nsData == null) {
            nowPlayingInfo.remove(MPMediaItemPropertyArtwork)
            NSOperationQueue.mainQueue().addOperationWithBlock {
                nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo.toMap()
            }
            return@withContext
        }

        UIImage.imageWithData(nsData)?.let { image ->
            nowPlayingInfo[MPMediaItemPropertyArtwork] =
                MPMediaItemArtwork(boundsSize = image.size) { _ -> image }

            NSOperationQueue.mainQueue().addOperationWithBlock {
                nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo.toMap()
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun ensureAudioSessionActive() {
        memScoped {
            val audioSession = AVAudioSession.sharedInstance()
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()

            audioSession.setActive(active = true, withOptions = 0u, error = errorPtr.ptr)
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun setUpAudioSession() {
        memScoped {
            val audioSession = AVAudioSession.sharedInstance()
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()

            if (!audioSession.setCategory(
                    category = AVAudioSessionCategoryPlayback,
                    mode = AVAudioSessionModeDefault,
                    options = 0u,
                    error = errorPtr.ptr
                )
            ) {
                errorPtr.value?.let { error ->
                    debugLog("Error setting audio session category: ${error.localizedDescription}")
                }
                return@memScoped
            }

            if (!audioSession.setActive(active = true, withOptions = 0u, error = errorPtr.ptr)) {
                errorPtr.value?.let { error ->
                    debugLog("Error activating audio session: ${error.localizedDescription}")
                }
            }
            NSNotificationCenter.defaultCenter().addObserverForName(
                name = "AVAudioSessionInterruptionNotification",
                `object` = audioSession,
                queue = NSOperationQueue.mainQueue(),
                usingBlock = { notification: NSNotification? ->
                    debugLog("AVAudioSessionInterruptionNotification")
                    notification?.userInfo?.let { userInfo ->
                        val interruptionType = userInfo[AVAudioSessionInterruptionTypeKey] as? NSNumber
                        val typeValue = interruptionType?.unsignedLongValue
                        debugLog("interruptionType: $typeValue")
                        when (typeValue) {
                            AVAudioSessionInterruptionTypeBegan -> pause()
                            AVAudioSessionInterruptionTypeEnded -> {
                                val options = userInfo[AVAudioSessionInterruptionOptionKey] as? NSNumber
                                if (options?.unsignedLongValue == AVAudioSessionInterruptionOptionShouldResume) {
                                    play()
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    private fun setupRemoteCommands() {
        remoteCommandCenter.playCommand.setEnabled(true)
        remoteCommandCenter.playCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            debugLog("playCommand")
            if (!isPlayingFlow.value) {
                play()
                MPRemoteCommandHandlerStatusSuccess
            } else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }

        remoteCommandCenter.pauseCommand.setEnabled(true)
        remoteCommandCenter.pauseCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            debugLog("pauseCommand")
            if (isPlayingFlow.value) {
                pause()
                MPRemoteCommandHandlerStatusSuccess
            } else {
                MPRemoteCommandHandlerStatusCommandFailed
            }
        }

        remoteCommandCenter.togglePlayPauseCommand.setEnabled(true)
        remoteCommandCenter.togglePlayPauseCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            if (isPlayingFlow.value) pause() else play()
            debugLog("togglePlayPauseCommand")
            MPRemoteCommandHandlerStatusSuccess
        }

        remoteCommandCenter.skipForwardCommand.setEnabled(true)
        remoteCommandCenter.skipForwardCommand.preferredIntervals = NSArray.arrayWithObject(NSNumber(double = 15.0))
        remoteCommandCenter.skipForwardCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            val seconds = (event as? MPSkipIntervalCommandEvent)?.interval ?: 15.0
            val current = currentPosition().toDouble().div(1000)
            seekTo(((current + seconds) * 1000).toLong())
            debugLog("skipForwardCommand: $seconds")
            MPRemoteCommandHandlerStatusSuccess
        }

        remoteCommandCenter.skipBackwardCommand.setEnabled(true)
        remoteCommandCenter.skipBackwardCommand.preferredIntervals = NSArray.arrayWithObject(NSNumber(double = 15.0))
        remoteCommandCenter.skipBackwardCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            val seconds = (event as? MPSkipIntervalCommandEvent)?.interval ?: 15.0
            val current = currentPosition().toDouble().div(1000)
            seekTo(((current - seconds).coerceAtLeast(0.0) * 1000).toLong())
            debugLog("skipBackwardCommand: $seconds")
            MPRemoteCommandHandlerStatusSuccess
        }

        remoteCommandCenter.nextTrackCommand.setEnabled(true)
        remoteCommandCenter.nextTrackCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            debugLog("nextTrackCommand")
            skipToNext()
            MPRemoteCommandHandlerStatusSuccess
        }

        remoteCommandCenter.previousTrackCommand.setEnabled(true)
        remoteCommandCenter.previousTrackCommand.addTargetWithHandler { event: MPRemoteCommandEvent? ->
            debugLog("previousTrackCommand")
            skipTpPrevious()
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private val endTimeObserver: (NSNotification?) -> Unit = { _ ->
        ensureAudioSessionActive()
        NSOperationQueue.mainQueue().addOperationWithBlock {
            debugLog("endTimeObserver")
//            val nextTrackPlayed = playNextTrack()
//
//            if (!nextTrackPlayed) {
//                listener?.onAudioCompleted()
//
//                player.pause()
//                player.seekToTime(CMTimeMake(value = 0, timescale = 1000))
//
//                currentTrack?.let { updateNowPlayingInfo(it) }
//            }
            skipToNext()
        }
    }

    @OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
    private fun startTimeObserver() {
        val interval = CMTimeMakeWithSeconds(1.0, NSEC_PER_SEC.toInt())
        timeObserver = player.addPeriodicTimeObserverForInterval(interval, queue = null, usingBlock = observer)

        if (notificationObserver != null) {
            NSNotificationCenter.defaultCenter().removeObserver(notificationObserver!!)
            notificationObserver = null
        }

        player.currentItem?.let { currentItem ->
            debugLog("startTimeObserver: ${currentItem.duration} ${currentItem.status} ${currentItem.loadedTimeRanges} ${currentItem.timedMetadata} ${currentItem.asset}")
            notificationObserver = NSNotificationCenter.defaultCenter().addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = currentItem,
                queue = NSOperationQueue.mainQueue(),
                usingBlock = endTimeObserver
            )
            currentItem.addObserver(
                observer = Observer {
                    val status = when (currentItem.status) {
                        AVPlayerStatusUnknown -> "AVPlayerStatusUnknown"
                        AVPlayerStatusReadyToPlay -> "AVPlayerStatusReadyToPlay"
                        AVPlayerStatusFailed -> "AVPlayerStatusFailed"
                        else -> "UNKNOWN"
                    }
                    currentItem.error?.let {
                        debugLog("error: ${it.domain} ${it.code} -> ${it.localizedDescription}")
                        debugLog("reason: ${it.localizedFailureReason}")
                        debugLog("suggestion: ${it.localizedRecoverySuggestion}")
                        debugLog("options: ${it.localizedRecoveryOptions}")
                        it.print()
                    }
                    debugLog("status change: $status")
                },
                forKeyPath = "status",
                options = NSKeyValueObservingOptionNew,
                context = null
            )
        } ?: run {
//            listener?.onError()
        }
    }

    private class Observer(
        private val callback: () -> Unit = {}
    ) : NSObject(), ObserverProtocol {
        override fun observeValueForKeyPath(
            keyPath: String?,
            ofObject: Any?,
            change: Map<Any?, *>?,
            context: COpaquePointer?
        ) {
            println("keyPath $keyPath")
            println("ofObject $ofObject")
            println("change $change")
            println("context $context")
            callback()
        }
    }

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

    override fun flattenPlaylist(): StateFlow<List<LAudio>> = flattenPlaylist
    override fun playlist(): StateFlow<List<LItem>> = playlist

    override fun updatePlaylist(playlist: List<LItem>) {
        launch { this@WrappedAVPlayer.playlist.emit(playlist) }
    }

    override fun clearPlaylist() {
        launch { this@WrappedAVPlayer.playlist.emit(emptyList()) }
    }

    override fun isPlaying(): StateFlow<Boolean> = isPlayingFlow

    override fun currentItem(): StateFlow<LAudio?> = currentItemFlow

    override fun currentItemIndex(): StateFlow<Int> = currentItemIndex

    override fun currentPlaybackState(): StateFlow<PlaybackState> = currentPlaybackState

    private fun playWithItem(item: LAudio) {
        prepareJob?.cancel()
        prepareJob = launch {
            ensureAudioSessionActive()
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

            ensureAudioSessionActive()

            player.pause()

            if (timeObserver != null) {
                player.removeTimeObserver(timeObserver!!)
                timeObserver = null
            }

            player.replaceCurrentItemWithPlayerItem(playerItem)

            startTimeObserver()

            player.play()

            currentItemIndex.value = flattenPlaylist.value.indexOf(item)
            updateNowPlayingInfo(item)
        }
    }

    override fun play() {
        ensureAudioSessionActive()
        if (player.currentItem != null) {
            debugLog("playing: ${player.currentItem} ${player.currentItem?.status}")

            player.play()
            isPlayingFlow.value = true
            currentItemFlow.value?.let { updateNowPlayingInfo(it) }
        } else {
            val current = currentItemFlow.value
                ?: throw Exception("No media to play")
            debugLog("playing: ${current.id} ${current.title} ${current.subtitle} ${current.mediaSourceName}")

            playWithItem(current)
        }
    }

    override fun pause() {
        player.pause()
        isPlayingFlow.value = false
        currentItemFlow.value?.let { updateNowPlayingInfo(it) }
    }

    override fun togglePlayPause() {
        if (isPlayingFlow.value) pause() else play()
    }

    override fun stop() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        isPlayingFlow.value = false
    }

    override fun skipTo(index: Int) {
        val targetItem = flattenPlaylist.value.getOrNull(index)
            ?: throw Exception("Invalid index")

        if (targetItem.id == currentItemFlow.value?.id) {
            seekTo(0)
        } else {
            playWithItem(targetItem)
        }
    }

    override fun skipToNext() {
        val nextIndex = (currentItemIndex.value + 1) % flattenPlaylist.value.size
        val nextItem = flattenPlaylist.value.getOrNull(nextIndex)
            ?: throw Exception("No next item")

        playWithItem(nextItem)
    }

    override fun skipTpPrevious() {
        val previousIndex = (currentItemIndex.value - 1 + flattenPlaylist.value.size) % flattenPlaylist.value.size
        val previousItem = flattenPlaylist.value.getOrNull(previousIndex)
            ?: throw Exception("No previous item")

        playWithItem(previousItem)
    }

    override fun seekTo(positionMs: Long) {
        val time = CMTimeMake(value = positionMs / 1000L, timescale = 1000)
        player.seekToTime(time)
    }

    override fun currentDuration(): Long {
        val currentItem = player.currentItem
        currentItem?.let {
            val duration = it.duration
            return CMTimeGetSeconds(duration).toLong() * 1000
        }
        return 0L
    }

    override fun currentPosition(): Long {
        val currentTime = player.currentTime()
        return CMTimeGetSeconds(currentTime).toLong() * 1000
    }

    override fun currentBufferedPosition(): Long {
        return currentPosition()
    }

    override fun errorMessage(): SharedFlow<Throwable> = errorSharedFlow
}

fun NSError.print() {
    val log = """
        [NSError: ${this.hashCode()}]
        [domain]: $domain
        [code]: $code
        [userInfo]: $userInfo
        [localizedDescription]: $localizedDescription
        [localizedFailureReason]: $localizedFailureReason
        [localizedRecoverySuggestion]: $localizedRecoverySuggestion
        [localizedRecoveryOptions]: $localizedRecoveryOptions
    """.trimIndent()
    Logger.e(tag = "NSError", messageString = log)
}