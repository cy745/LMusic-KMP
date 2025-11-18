package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.util.flatten
import com.lalilu.lplayer.helper.*
import com.lalilu.lplayer.notifacation.NowPlayingInfoNotification
import com.lalilu.lplayer.notifacation.RemoteCommandHandler
import kotlinx.cinterop.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.AVFAudio.AVAudioPlayer
import platform.AVFoundation.*
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeMake
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
class AVPlayerPlayback : AbstractPlayback(), KoinComponent {
    companion object Companion {
        const val TAG = "WrappedAVPlayer"
    }

    fun debugLog(message: String) {
        Logger.i(tag = TAG, messageString = message)
    }

    private val observerContext: COpaquePointer = cOpaquePtr()
    private val platformMediaSource: PlatformMediaSource by inject()
    private val errorPtr = nativeHeap.alloc<ObjCObjectVar<NSError?>>()
    val player: AVPlayer = AVPlayer()
    var audioPlayer: AVAudioPlayer? = null

    init {
        NowPlayingInfoNotification.bindPlayback(this)
        RemoteCommandHandler.bindPlayback(this)
        if (AudioSessionHelper.setUpAudioSession()) {
            AudioSessionHelper.bindPlayback(this)
        }
    }

    private val observer = Observer { keyPath, ofObject, change, context ->
        if (observerContext != context) return@Observer
        val playerItem = ofObject as AVPlayerItem
        when (playerItem.status) {
            AVPlayerStatusUnknown -> {
                debugLog("status: AVPlayerStatusUnknown")
            }

            AVPlayerStatusReadyToPlay -> {
                debugLog("status: AVPlayerStatusReadyToPlay")
                _isPlaying.value = true
                playerItem.duration.useContents { _currentDuration.value = toMilliseconds().toLong() }
                updateNavigationCapabilities()
            }

            AVPlayerStatusFailed -> {
                debugLog("status: AVPlayerStatusFailed")
                playerItem.error?.print()
            }
        }
    }

    override suspend fun playItem(item: LAudio) {
        AudioSessionHelper.ensureAudioSessionActive()
        val source = platformMediaSource.sources
            .firstOrNull { item.mediaSourceName == it.name }
            ?: throw Exception("No source item found for ${item.mediaSourceName}")

        when (val data = source.dataSource.getMedia(item)) {
            is MediaData.Url -> {
                // 移除旧的监听
                player.currentItem?.removeObserver(
                    forKeyPath = "status",
                    observer = observer,
                    context = observerContext
                )

                player.pause()

                Logger.i(tag = "AVPlayer", messageString = "prepared with url: ${data.url}")
                val url = NSURL.URLWithString(data.url)!!
                val playerItem = AVPlayerItem(url)

                // 监听playerItem的状态变化
                playerItem.observeFor(
                    keyPath = "status",
                    observer = observer,
                    context = observerContext
                )

                _currentItemIndex.value = _playlist.value.flatten().indexOf(item)
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

                AVAudioPlayerDidPlayToEndHelper.observe(
                    player = player,
                    onFinishPlaying = { _, isSuccess ->
                        debugLog("AVAudioPlayerDidPlayToEndTimeNotification: $isSuccess")
                        launch { this@AVPlayerPlayback.skipToNext() }
                    },
                    onEndInterruptionWithFlags = { _, flags ->
                        debugLog("AVAudioPlayerDidEndInterruptionWithFlags: $flags")
                    },
                    onDecodeErrorDidOccur = { _, error ->
                        debugLog("AVAudioPlayerDidDecodeErrorDidOccur: $error")
                    },
                    onBeginInterruption = {
                        val duration = audioPlayer?.duration
                        _currentDuration.value = duration?.toLong() ?: 0L
                        debugLog("AVAudioPlayerDidBeginInterruption: duration: $duration")
                    },
                    onEndInterruption = {
                        debugLog("AVAudioPlayerDidEndInterruption")
                    }
                )

                player.prepareToPlay()
                player.play()

                _isPlaying.value = true
                _currentItemIndex.value = _playlist.value.flatten().indexOf(item)
                _currentDuration.value = (player.duration * 1000L).toLong()
                updateNavigationCapabilities()

                audioPlayer?.stop()
                audioPlayer = player
            }

            else -> {
                throw Exception("Unsupported source item: $data")
            }
        }
    }

    override suspend fun play() {
        try {
            AudioSessionHelper.ensureAudioSessionActive()
            // 若audioPlayer存在，则直接播放
            if (audioPlayer != null) {
                debugLog("audioPlayer playing: ${audioPlayer?.currentTime} ${audioPlayer?.duration}")

                audioPlayer?.play()
                _isPlaying.value = true
                return
            }

            // 若player存在播放中的元素，则直接播放
            if (player.currentItem != null) {
                debugLog("player playing: ${player.currentItem} ${player.currentItem?.status}")

                player.play()
                _isPlaying.value = true
                return
            }

            // 获取当前播放元素，并进行播放
            val current = currentItem.value
                ?: throw Exception("No media to play")
            debugLog("playing: ${current.id} ${current.title} ${current.subtitle} ${current.mediaSourceName}")

            playItem(current)
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun pause() {
        try {
            if (audioPlayer != null) {
                audioPlayer?.pause()
            } else {
                player.pause()
            }

            _isPlaying.value = false
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    override suspend fun stop() {
        try {
            audioPlayer?.stop()
            audioPlayer = null
            player.pause()
            player.replaceCurrentItemWithPlayerItem(null)
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
            if (audioPlayer != null) {
                audioPlayer?.playAtTime(positionMs / 1000.0)
            } else {
                val time = CMTimeMake(value = positionMs, timescale = 1000)
                player.seekToTime(time)
            }
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override fun currentPosition(): Long {
        return if (audioPlayer != null) {
            (audioPlayer!!.currentTime * 1000).toLong()
        } else {
            memScoped {
                // AVPlayer的currentTime返回的是ns，需要转换成ms
                player.currentTime()
                    .useContents { value / 1000L / 1000L }
            }
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

// 扩展函数：获取精确毫秒数（Double 类型，保留小数，适合需要高精度的场景）
fun CMTime.toMilliseconds(): Double {
    // 安全检查：timescale 不能为 0，否则返回 0.0
    if (timescale == 0) return 0.0
    // 核心计算：(value / timescale) * 1000 → 转换为毫秒
    return (value.toDouble() / timescale.toDouble()) * 1000.0
}