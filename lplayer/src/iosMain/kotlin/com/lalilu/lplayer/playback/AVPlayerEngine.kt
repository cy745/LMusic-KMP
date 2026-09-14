package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lplayer.helper.*
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFoundation.*
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.Foundation.removeObserver

/**
 * Engine 封装 [AVPlayer]，处理 [MediaData.Url] 类型的媒体。
 *
 * 内部持有单个 [AVPlayer] 实例（复用），每次 [load] 时
 * [replaceCurrentItemWithPlayerItem] 切换媒体。通过 KVO 监听
 * playerItem.status 获取就绪/失败状态。
 */
@OptIn(ExperimentalForeignApi::class)
class AVPlayerEngine : PlaybackEngine {

    companion object {
        const val TAG = "AVPlayerEngine"
    }

    private val logger = Logger.withTag(TAG)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val avPlayer = AVPlayer()
    private val _state = MutableStateFlow(PlaybackEngineState.EMPTY)
    override val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null

    private val observerContext: COpaquePointer = cOpaquePtr()
    private var autoPlayWhenReady = false

    /** 当前已加载的 AVPlayerItem，用于生命周期管理 */
    private var currentItem: AVPlayerItem? = null

    private val statusObserver = Observer { keyPath, ofObject, change, context ->
        if (observerContext != context) return@Observer
        val playerItem = ofObject as AVPlayerItem
        if (playerItem != currentItem) return@Observer
        when (playerItem.status) {
            AVPlayerStatusUnknown -> {
                logger.i(messageString = "AVPlayerStatusUnknown")
            }

            AVPlayerStatusReadyToPlay -> {
                logger.i(messageString = "AVPlayerStatusReadyToPlay")
                val duration = playerItem.duration.useContents { toMilliseconds().toLong() }
                _state.update { it.copy(isLoading = false, duration = duration) }

                if (autoPlayWhenReady) {
                    avPlayer.play()
                    _state.update { it.copy(isPlaying = true) }
                    autoPlayWhenReady = false
                }
            }

            AVPlayerStatusFailed -> {
                val err = playerItem.error
                val errorMsg = err?.description ?: "AVPlayerStatusFailed"
                logger.i(messageString = errorMsg)
                _state.update { it.copy(isLoading = false, error = errorMsg) }
            }
        }
    }

    override fun canHandle(mediaData: MediaData, audio: LAudio): Boolean =
        mediaData is MediaData.Url

    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        autoPlayWhenReady = false
        _state.value = PlaybackEngineState(isLoading = true)

        // 清理旧的播放完成监听
        currentItem?.let { oldItem ->
            AVPlayerItemDidPlayToEndTimeNotification?.let { AVPlayerItemEventObserver.removeObserver(it) }
            oldItem.removeObserver(
                observer = statusObserver,
                forKeyPath = "status",
                context = observerContext
            )
        }

        avPlayer.pause()

        val urlStr = (mediaData as MediaData.Url).url
        logger.i(messageString = "loading url: $urlStr")
        val url = NSURL.URLWithString(urlStr)
            ?: throw Exception("Invalid URL: $urlStr")
        val playerItem = AVPlayerItem(url)
        // Registering KVO may synchronously deliver the initial status.
        currentItem = playerItem

        // 监听状态变化
        playerItem.observeFor(
            keyPath = "status",
            observer = statusObserver,
            context = observerContext
        )

        // 监听播放完成
        AVPlayerItemEventObserver.observe(AVPlayerItemDidPlayToEndTimeNotification, playerItem) {
            if (currentItem != playerItem) return@observe
            logger.i(messageString = "AVPlayerItemDidPlayToEndTimeNotification")
            onEvent?.invoke(PlaybackEngineEvent.Completion)
        }

        // 切换播放项
        avPlayer.replaceCurrentItemWithPlayerItem(playerItem)
        currentItem = playerItem

        // 启动 position 监听
        AVPlayerPositionObserver.observe(avPlayer) { seconds ->
            if (currentItem == playerItem) {
                _state.update { it.copy(position = (seconds * 1000).toLong()) }
            }
        }

        try {
            val ready = withTimeout(30_000) { state.first { !it.isLoading } }
            if (currentItem != playerItem) throw CancellationException("Media selection replaced")
            check(ready.error == null && playerItem.status == AVPlayerStatusReadyToPlay) {
                ready.error ?: "AVPlayer did not prepare the selected item"
            }
        } catch (cancelled: CancellationException) {
            // A cancelled load must not later start playing or publish ready for this item.
            // Never release a newer selection that has already replaced it.
            if (currentItem == playerItem) release()
            throw cancelled
        }
    }

    override suspend fun play() {
        if (currentItem?.status == AVPlayerStatusReadyToPlay) {
            AudioSessionHelper.ensureAudioSessionActive()
            avPlayer.play()
            _state.update { it.copy(isPlaying = true) }
        } else {
            autoPlayWhenReady = true
        }
    }

    override suspend fun pause() {
        autoPlayWhenReady = false
        avPlayer.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    override suspend fun stop() {
        autoPlayWhenReady = false
        avPlayer.pause()
        avPlayer.seekToTime(CMTimeMake(value = 0, timescale = 1000))
        // Stop retains the item: it must not pretend an in-flight load is prepared.
        _state.update { it.copy(isPlaying = false, position = 0L) }
    }

    override suspend fun seekTo(positionMs: Long) {
        val time = CMTimeMake(value = positionMs, timescale = 1000)
        avPlayer.seekToTime(time)
        _state.update { it.copy(position = positionMs) }
    }

    /** AVPlayer 音量（用于 VolumeFadeHelper 淡入淡出） */
    fun setVolume(volume: Float) {
        avPlayer.setVolume(volume)
    }

    override fun currentPosition(): Long {
        return avPlayer.currentTime().useContents { toMilliseconds().toLong() }
    }

    override suspend fun release() {
        autoPlayWhenReady = false
        avPlayer.pause()

        currentItem?.let {
            AVPlayerItemDidPlayToEndTimeNotification?.let { AVPlayerItemEventObserver.removeObserver(it) }
            it.removeObserver(
                observer = statusObserver,
                forKeyPath = "status",
                context = observerContext
            )
        }

        AVPlayerPositionObserver.removeObserver(avPlayer)
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        currentItem = null

        _state.value = PlaybackEngineState.EMPTY
    }
}

/** CMTime 转毫秒（调用方应在 useContents 块内使用） */
fun CMTime.toMilliseconds(): Double {
    if (timescale == 0) return 0.0
    return (value.toDouble() / timescale.toDouble()) * 1000.0
}
