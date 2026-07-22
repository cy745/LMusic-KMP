package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lplayer.helper.AVAudioPlayerDidPlayToEndHelper
import com.lalilu.lplayer.helper.AudioSessionHelper
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.dataWithBytes

/**
 * Engine 封装 [AVAudioPlayer]，处理 [MediaData.Bytes] 类型的媒体。
 *
 * 每次 [load] 时创建一个新的 [AVAudioPlayer] 实例，
 * [release] 时销毁。不适用于预加载场景（需要完整字节数组）。
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class AVAudioPlayerEngine : PlaybackEngine {

    companion object {
        const val TAG = "AVAudioPlayerEngine"
    }

    private val logger = Logger.withTag(TAG)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(PlaybackEngineState.EMPTY)
    override val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null

    private val errorPtr = nativeHeap.alloc<ObjCObjectVar<NSError?>>()
    private var currentPlayer: AVAudioPlayer? = null

    override fun canHandle(mediaData: MediaData, audio: LAudio): Boolean =
        mediaData is MediaData.Bytes

    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        release()
        _state.value = PlaybackEngineState(isLoading = true)

        val bytes = (mediaData as MediaData.Bytes).bytes
        logger.i(messageString = "loading bytes: ${bytes.size}")

        val player = memScoped {
            val data = NSData.dataWithBytes(
                bytes = bytes.refTo(0).getPointer(this),
                length = bytes.size.toULong()
            )
            AVAudioPlayer(data, errorPtr.ptr)
        }

        // 注册播放完成/中断回调
        AVAudioPlayerDidPlayToEndHelper.observe(
            player = player,
            onFinishPlaying = { _, isSuccess ->
                logger.i(messageString = "AVAudioPlayerDidFinishPlaying: $isSuccess")
                scope.launch {
                    onEvent?.invoke(PlaybackEngineEvent.Completion)
                }
            },
            onEndInterruptionWithFlags = { _, flags ->
                logger.i(messageString = "AVAudioPlayerEndInterruptionWithFlags: $flags")
            },
            onDecodeErrorDidOccur = { _, error ->
                logger.i(messageString = "AVAudioPlayerDecodeErrorDidOccur: ${error?.description}")
            },
            onBeginInterruption = {
                logger.i(messageString = "AVAudioPlayerBeginInterruption")
            },
            onEndInterruption = {
                logger.i(messageString = "AVAudioPlayerEndInterruption")
            }
        )

        player.prepareToPlay()
        val duration = (player.duration * 1000L).toLong()

        currentPlayer = player
        _state.value = PlaybackEngineState(
            isLoading = false,
            duration = duration
        )
    }

    override suspend fun play() {
        AudioSessionHelper.ensureAudioSessionActive()
        currentPlayer?.play()
        _state.update { it.copy(isPlaying = true) }
    }

    override suspend fun pause() {
        currentPlayer?.pause()
        _state.update { it.copy(isPlaying = false) }
    }

    override suspend fun stop() {
        currentPlayer?.stop()
        currentPlayer?.currentTime = 0.0
        _state.value = PlaybackEngineState(duration = _state.value.duration)
    }

    override suspend fun seekTo(positionMs: Long) {
        currentPlayer?.currentTime = positionMs / 1000.0
        _state.update { it.copy(position = positionMs) }
    }

    /** AVAudioPlayer 音量（用于 VolumeFadeHelper 淡入淡出） */
    fun setVolume(volume: Float) {
        currentPlayer?.volume = volume
    }

    override fun currentPosition(): Long {
        return (currentPlayer?.currentTime?.times(1000))?.toLong() ?: 0L
    }

    override suspend fun release() {
        currentPlayer?.stop()
        currentPlayer?.delegate = null
        currentPlayer = null
        _state.value = PlaybackEngineState.EMPTY
    }
}
