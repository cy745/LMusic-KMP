package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.MusicKitPlayerController
import com.lalilu.lmedia.MusicKitPlayerControllerDelegateProtocol
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * Engine 封装 [ApplicationMusicPlayer]，处理 MusicKitSource 的歌曲播放。
 *
 * Apple Music 歌曲受 FairPlay DRM 保护，不能通过 AVPlayer 播放，
 * 必须通过 MusicKit 的 [ApplicationMusicPlayer]。
 *
 * 该 Engine 通过 [MusicKitPlayerController]（Swift 端 ObjC 包装）
 * 与 [ApplicationMusicPlayer] 通信，使用 storeID 标识歌曲。
 *
 * 使用前提：
 * 1. [MusicKitSource] 必须在 [LAudio.extra] 中提供 "storeID"
 * 2. [MusicKitPlayerController.shared] 必须已通过 [configure] 配置了 song cache
 *    （[MusicKitWrapper.fetchUserLibrarySongs] 在返回时自动配置）
 */
@OptIn(ExperimentalForeignApi::class)
class MusicKitEngine : PlaybackEngine {

    companion object {
        const val TAG = "MusicKitEngine"
    }

    private val logger = Logger.withTag(TAG)
    internal val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val controller = MusicKitPlayerController.shared()
    private val delegate = MusicKitPlaybackDelegate(engine = this)

    internal val _state = MutableStateFlow(PlaybackEngineState.EMPTY)
    override val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null

    private var loadedStoreID: String? = null

    init {
        controller!!.setDelegate(delegate)
    }

    override fun canHandle(mediaData: MediaData, audio: LAudio): Boolean {
        val result = audio.mediaSourceName == "MusicKitSource"
        logger.i(messageString = "canHandle(audio=${audio.id}, source=${audio.mediaSourceName}) → $result")
        return result
    }

    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        val storeID = audio.extra?.get("storeID") as? String
            ?: throw Exception("MusicKit: storeID not found in LAudio.extra for ${audio.id}")

        loadedStoreID = storeID
        _state.value = PlaybackEngineState(isLoading = true)

        logger.i(messageString = "loading storeID=$storeID title=${audio.title} artist=${audio.subtitle}")

        try {
            controller!!.setQueueWithStoreID(storeID)
        } catch (e: Exception) {
            logger.e(tag = TAG, messageString = "setQueueWithStoreID failed", throwable = e)
            _state.value = PlaybackEngineState(isLoading = false, error = e.message)
            throw e
        }
        // State is updated asynchronously via delegate callbacks
    }

    override suspend fun play() {
        logger.i(messageString = "resume playback (storeID=$loadedStoreID)")
        try {
            controller!!.resumePlayback()
        } catch (e: Exception) {
            logger.e(tag = TAG, messageString = "resumePlayback failed", throwable = e)
            _state.update { it.copy(error = e.message) }
        }
    }

    override suspend fun pause() {
        logger.i(messageString = "pause playback")
        try {
            controller!!.pausePlayback()
        } catch (e: Exception) {
            logger.e(tag = TAG, messageString = "pausePlayback failed", throwable = e)
        }
        _state.update { it.copy(isPlaying = false) }
    }

    override suspend fun stop() {
        logger.i(messageString = "stop playback")
        try {
            controller!!.stopPlayback()
        } catch (e: Exception) {
            logger.e(tag = TAG, messageString = "stopPlayback failed", throwable = e)
        }
        _state.value = PlaybackEngineState.EMPTY
        loadedStoreID = null
    }

    override suspend fun seekTo(positionMs: Long) {
        logger.i(messageString = "seekTo: ${positionMs}ms")
        try {
            controller!!.seekTo(positionMs / 1000.0)
        } catch (e: Exception) {
            logger.e(tag = TAG, messageString = "seekTo failed", throwable = e)
        }
        _state.update { it.copy(position = positionMs) }
    }

    override fun currentPosition(): Long {
        return (controller!!.currentPlaybackTime * 1000).toLong()
    }

    override suspend fun release() {
        logger.i(messageString = "release")
        try {
            controller!!.stopPlayback()
            controller!!.invalidate()
        } catch (e: Exception) {
            logger.e(tag = TAG, messageString = "release failed", throwable = e)
        }
        _state.value = PlaybackEngineState.EMPTY
        loadedStoreID = null
    }
}

/**
 * Kotlin 实现 [MusicKitPlayerControllerDelegateProtocol] 以接收来自
 * Swift MusicKitPlayerController 的状态更新和事件通知。
 */
@OptIn(ExperimentalForeignApi::class)
private class MusicKitPlaybackDelegate(
    private val engine: MusicKitEngine,
) : NSObject(), MusicKitPlayerControllerDelegateProtocol {

    private val logger = Logger.withTag("MusicKitPlaybackDelegate")

    override fun onPlaybackStateChangedWithIsPlaying(
        isPlaying: Boolean,
        playbackTime: Double,
        duration: Double
    ) {
        logger.i(messageString = "state: isPlaying=$isPlaying time=$playbackTime duration=$duration")
        engine._state.update {
            PlaybackEngineState(
                isPlaying = isPlaying,
                position = (playbackTime * 1000).toLong(),
                duration = (duration * 1000).toLong(),
                isLoading = false,
            )
        }
    }

    override fun onDidFinishPlaying() {
        logger.i(messageString = "did finish playing")
        engine._state.update { it.copy(isPlaying = false) }
        engine.engineScope.launch {
            engine.onEvent?.invoke(PlaybackEngineEvent.Completion)
        }
    }

    override fun onPlaybackErrorWithError(error: NSError?) {
        val msg = error?.localizedDescription ?: "Unknown MusicKit error"
        val code = error?.code ?: -1
        logger.e(
            tag = "MusicKitPlaybackDelegate",
            messageString = "error: $msg (code=$code)"
        )
        engine._state.update {
            PlaybackEngineState(isLoading = false, error = msg)
        }
        engine.engineScope.launch {
            engine.onEvent?.invoke(
                PlaybackEngineEvent.Error(
                    Exception("MusicKit: $msg")
                )
            )
        }
    }
}
