package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.util.flatten
import com.lalilu.lplayer.helper.AVAudioPlayerDidPlayToEndHelper
import com.lalilu.lplayer.helper.AVPlayerItemEventObserver
import com.lalilu.lplayer.helper.AudioSessionHelper
import com.lalilu.lplayer.helper.observeFor
import com.lalilu.lplayer.notifacation.NowPlayingInfoNotification
import com.lalilu.lplayer.notifacation.RemoteCommandHandler
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.AVFAudio.AVAudioPlayer
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalForeignApi::class)
class AVPlayerPlayback : Playback, CoroutineScope, KoinComponent {
    companion object Companion {
        const val TAG = "WrappedAVPlayer"
    }

    fun debugLog(message: String) {
        Logger.i(tag = TAG, messageString = message)
    }

    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val platformMediaSource: PlatformMediaSource by inject()
    private var prepareJob: Job? = null

    private val errorPtr = nativeHeap.alloc<ObjCObjectVar<NSError?>>()
    val player: AVPlayer = AVPlayer()
    var audioPlayer: AVAudioPlayer? = null

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

    init {
        NowPlayingInfoNotification.bindPlayback(this)
        RemoteCommandHandler.bindPlayback(this)
        if (AudioSessionHelper.setUpAudioSession()) {
            AudioSessionHelper.bindPlayback(this)
        }
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

    private fun playWithItem(item: LAudio) = runWith {
        prepareJob?.cancel()
        prepareJob = launch {
            runWithSuspend {
                AudioSessionHelper.ensureAudioSessionActive()
                val source = platformMediaSource.sources
                    .firstOrNull { item.mediaSourceName == it.name }
                    ?: throw Exception("No source item found for ${item.mediaSourceName}")

                val data = source.dataSource.getMedia(item)
                when (data) {
                    is MediaData.Url -> {
                        Logger.i(tag = "AVPlayer", messageString = "prepared with url: ${data.url}")
                        val url = NSURL.URLWithString(data.url)!!
                        val playerItem = AVPlayerItem(url)

                        player.pause()

                        // 监听playerItem的状态变化
                        playerItem.observeFor("status") {
                            when (it.status) {
                                AVPlayerStatusUnknown -> {
                                    debugLog("status: AVPlayerStatusUnknown")
                                }

                                AVPlayerStatusReadyToPlay -> {
                                    debugLog("status: AVPlayerStatusReadyToPlay")
                                    isPlayingFlow.value = true
                                    currentItemIndex.value = flattenPlaylist.value.indexOf(item)
                                }

                                AVPlayerStatusFailed -> {
                                    debugLog("status: AVPlayerStatusFailed")
                                    it.error?.let {
                                        debugLog("error: ${it.domain} ${it.code} -> ${it.localizedDescription}")
                                        debugLog("reason: ${it.localizedFailureReason}")
                                        debugLog("suggestion: ${it.localizedRecoverySuggestion}")
                                        debugLog("options: ${it.localizedRecoveryOptions}")
                                    }
                                    it.error?.print()
                                }
                            }
                        }

                        // 监听进度变化
//                        AVPlayerPositionObserver.observe(player) {
//                            debugLog("position: $it")
//                        }
                        player.replaceCurrentItemWithPlayerItem(playerItem)
                        player.play()

                        // 监听播放完成事件
                        AVPlayerItemEventObserver.observe(
                            key = AVPlayerItemDidPlayToEndTimeNotification,
                            target = playerItem,
                            callback = {
                                debugLog("AVPlayerItemDidPlayToEndTimeNotification")
                                this@AVPlayerPlayback.skipToNext()
                            }
                        )

                        audioPlayer?.stop()
                        audioPlayer = null
                    }

                    is MediaData.Bytes -> {
                        Logger.i(tag = "AVPlayer", messageString = "prepared with bytes: ${data.bytes.size}")

                        val player = memScoped {
                            val data = NSData.dataWithBytes(
                                bytes = data.bytes.refTo(0).getPointer(this),
                                length = data.bytes.size.toULong()
                            )
                            AVAudioPlayer(data, errorPtr.ptr)
                        }

                        player.prepareToPlay()
                        player.play()

                        isPlayingFlow.value = true
                        currentItemIndex.value = flattenPlaylist.value.indexOf(item)

                        AVAudioPlayerDidPlayToEndHelper.observe(
                            player = player,
                            onFinishPlaying = { _, isSuccess ->
                                debugLog("AVAudioPlayerDidPlayToEndTimeNotification: $isSuccess")
                                this@AVPlayerPlayback.skipToNext()
                            },
                            onEndInterruptionWithFlags = { _, flags ->
                                debugLog("AVAudioPlayerDidEndInterruptionWithFlags: $flags")
                            },
                            onDecodeErrorDidOccur = { _, error ->
                                debugLog("AVAudioPlayerDidDecodeErrorDidOccur: $error")
                            },
                            onBeginInterruption = {
                                debugLog("AVAudioPlayerDidBeginInterruption")
                            },
                            onEndInterruption = {
                                debugLog("AVAudioPlayerDidEndInterruption")
                            }
                        )

                        audioPlayer?.stop()
                        audioPlayer = player
                    }

                    else -> {
                        throw Exception("Unsupported source item: $data")
                    }
                }
            }
        }
    }

    override fun play() = runWith {
        AudioSessionHelper.ensureAudioSessionActive()
        // 若audioPlayer存在，则直接播放
        if (audioPlayer != null) {
            debugLog("audioPlayer playing: ${audioPlayer?.currentTime} ${audioPlayer?.duration}")

            audioPlayer?.play()
            isPlayingFlow.value = true
            return@runWith
        }

        // 若player存在播放中的元素，则直接播放
        if (player.currentItem != null) {
            debugLog("player playing: ${player.currentItem} ${player.currentItem?.status}")

            player.play()
            isPlayingFlow.value = true
            return@runWith
        }

        // 获取当前播放元素，并进行播放
        val current = currentItemFlow.value
            ?: throw Exception("No media to play")
        debugLog("playing: ${current.id} ${current.title} ${current.subtitle} ${current.mediaSourceName}")

        playWithItem(current)
    }

    override fun pause() = runWith {
        if (audioPlayer != null) {
            audioPlayer?.pause()
        } else {
            player.pause()
        }

        isPlayingFlow.value = false
    }

    override fun togglePlayPause() {
        if (isPlayingFlow.value) pause() else play()
    }

    override fun stop() = runWith {
        audioPlayer?.stop()
        audioPlayer = null
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

    override fun skipToNext(): Unit = runWith {
        val nextIndex = (currentItemIndex.value + 1) % flattenPlaylist.value.size
        val nextItem = flattenPlaylist.value.getOrNull(nextIndex)
            ?: throw Exception("No next item")

        playWithItem(nextItem)
    }

    override fun skipTpPrevious(): Unit = runWith {
        val previousIndex = (currentItemIndex.value - 1 + flattenPlaylist.value.size) % flattenPlaylist.value.size
        val previousItem = flattenPlaylist.value.getOrNull(previousIndex)
            ?: throw Exception("No previous item")

        playWithItem(previousItem)
    }

    override fun seekTo(positionMs: Long) = runWith {
        if (audioPlayer != null) {
            audioPlayer?.playAtTime(positionMs / 1000.0)
        } else {
            val time = CMTimeMake(value = positionMs, timescale = 1000)
            player.seekToTime(time)
        }
    }

    override fun currentDuration(): Long = runWith(0L) {
        if (audioPlayer != null) {
            return@runWith audioPlayer?.duration()?.toLong() ?: 0L
        }

        val seconds = player.currentItem
            ?.let { CMTimeGetSeconds(it.duration) }
            ?: 0.0

        return@runWith seconds
            .times(1000.0)
            .toLong()
    }

    override fun currentPosition(): Long = runWith(0L) {
        if (audioPlayer != null) {
            return@runWith audioPlayer?.currentTime()?.toLong() ?: 0L
        }

        val currentTime = player.currentTime()
        val seconds = CMTimeGetSeconds(currentTime)

        return@runWith seconds
            .times(1000.0)
            .toLong()
    }

    override fun currentBufferedPosition(): Long {
        return currentPosition()
    }

    override fun errorMessage(): SharedFlow<Throwable> = errorSharedFlow

    private suspend fun runWithSuspend(callback: suspend () -> Unit) {
        try {
            callback()
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            launch { errorSharedFlow.emit(e) }
        }
    }

    private fun runWith(callback: () -> Unit) {
        try {
            callback()
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            launch { errorSharedFlow.emit(e) }
        }
    }

    private fun <T> runWith(default: T, callback: () -> T): T {
        return try {
            callback()
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            launch { errorSharedFlow.emit(e) }
            default
        }
    }
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