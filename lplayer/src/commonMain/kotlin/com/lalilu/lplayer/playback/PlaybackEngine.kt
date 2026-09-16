package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Engine 内部连续状态，由 [PlaybackEngine.state] 以 StateFlow 形式暴露。
 *
 * 生命周期流转：
 *   EMPTY → (load) → isLoading=true → (准备就绪) → isLoading=false
 *       ├── play()  → isPlaying=true
 *       ├── pause() → isPlaying=false
 *       └── release() → EMPTY
 */
data class PlaybackEngineState(
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    companion object {
        val EMPTY = PlaybackEngineState()
    }
}

/**
 * Engine 上报的离散事件，通过 [PlaybackEngine.onEvent] 回调传递给 AbstractPlayback。
 * 连续状态走 [PlaybackEngine.state]，一次性事件走此回调。
 */
sealed interface PlaybackEngineEvent {
    data object Completion : PlaybackEngineEvent
    data class Error(val throwable: Throwable) : PlaybackEngineEvent
}

/**
 * 播放技术引擎接口。
 *
 * 每个 Engine 封装一种互斥的底层播放技术（AVPlayer / AVAudioPlayer / Media3 ExoPlayer / vlcj 等），
 * 通过 [canHandle] 声明自己能处理的媒体数据类型，由 [PlaybackEngineRouter] 链式匹配选择。
 *
 * Engine 内部自行管理 player 实例的生命周期（复用/按需创建/系统单例），
 * 对外暴露统一的 [state]（连续状态）和 [onEvent]（离散事件）。
 */
interface PlaybackEngine {
    /** 连续状态：isPlaying / position / duration 等 */
    val state: StateFlow<PlaybackEngineState>

    /**
     * 声明当前 Engine 能否处理给定的媒体数据。
     * 由 [PlaybackEngineRouter] 在链式匹配时调用。
     */
    fun canHandle(mediaData: MediaData, audio: LAudio): Boolean

    /**
     * 发起加载。**返回不代表已经可以播放**：只表示加载请求已被接受。
     *
     * 这个区分是必要的：选曲命令位于播放器的队列编辑边界内，如果在这里等原生加载，
     * 慢加载（原生上限 30s）会把点歌和队列编辑一起阻塞。失败与就绪由 [state] 暴露给观察者；
     * 需要在命令内确认就绪的调用方（例如历史恢复）显式调用 [awaitPreparation]。
     */
    suspend fun load(mediaData: MediaData, audio: LAudio)

    /**
     * 等待本次加载就绪：返回即确认可以播放；失败抛出；取消时本次加载作废。
     *
     * 默认实现轮询 [state]；平台可覆盖以补充"取消时只释放仍属于本次加载的媒体"等语义。
     */
    suspend fun awaitPreparation(timeout: Duration = DEFAULT_PREPARATION_TIMEOUT) {
        val ready = withTimeout(timeout) { state.first { !it.isLoading } }
        check(ready.error == null) { ready.error ?: "Media did not prepare" }
    }

    /** 开始或恢复播放 */
    suspend fun play()

    /** 暂停播放（保持当前位置） */
    suspend fun pause()

    /** 停止播放（回到起始位置，媒体仍保持加载） */
    suspend fun stop()

    /** 跳转到指定位置 */
    suspend fun seekTo(positionMs: Long)

    /** 获取当前播放位置（毫秒）。同步取值，与 state.position 等价。 */
    fun currentPosition(): Long

    /** 卸载当前媒体。Engine 实例不会被销毁，可再次 [load]。 */
    suspend fun release()

    /**
     * 离散事件回调，由 AbstractPlayback 在 Engine 初始化后绑定。
     * Engine 在合适的时机（播放完成、出错等）调用此回调。
     */
    var onEvent: (suspend (PlaybackEngineEvent) -> Unit)?
}

/** 原生加载的默认等待上限；超过后按加载失败处理，由调用方决定重试或跳过。 */
val DEFAULT_PREPARATION_TIMEOUT: Duration = 30.seconds
