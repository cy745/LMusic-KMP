package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放器时钟的稳定容器。
 *
 * [position] 的对象身份在播放页生命周期内保持不变，确保 Seekbar 的跟踪、拖动和回正逻辑
 * 不会因切歌而重建。[sampledPlaybackKey] 只表示最近一次主动读取 position 时使用的媒体身份，
 * 供歌词切换判断旧 position 是否已经失效，不参与 Seekbar 的任何计算。
 */
@Stable
internal class PlaybackPositionState(
    initialPosition: Long,
    initialPlaybackKey: Any?,
) {
    val position: MutableLongState = mutableLongStateOf(initialPosition)

    var sampledPlaybackKey: Any? by mutableStateOf(initialPlaybackKey)
        private set

    fun synchronize(playbackKey: Any?, position: Long) {
        // 先切换 position 的归属，旧歌词会立即停止读取，不会短暂消费新歌的位置。
        sampledPlaybackKey = playbackKey
        this.position.longValue = position
    }

    fun updatePosition(position: Long) {
        this.position.longValue = position
    }
}

@Composable
internal fun rememberPlaybackPositionState(
    isPlaying: State<Boolean>,
    playbackKey: Any?,
    readPosition: () -> Long = { LPlayer.instance.currentPosition() },
    frozen: () -> Boolean = { false },
): PlaybackPositionState {
    val state = remember {
        PlaybackPositionState(
            initialPosition = readPosition(),
            initialPlaybackKey = playbackKey,
        )
    }
    PlaybackPositionEffect(state, isPlaying, playbackKey, readPosition, frozen)
    return state
}

/** Unit 返回值为时钟任务提供独立 restart scope，播放状态只在这里读取。 */
@Composable
internal fun PlaybackPositionEffect(
    state: PlaybackPositionState,
    isPlaying: State<Boolean>,
    playbackKey: Any?,
    readPosition: () -> Long,
    frozen: () -> Boolean,
) {
    val playing = isPlaying.value
    val currentReadPosition = rememberUpdatedState(readPosition)
    val currentFrozen = rememberUpdatedState(frozen)
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(playing, playbackKey) {
        val job = scope.launch {
            state.synchronize(playbackKey, currentReadPosition.value())
            while (isActive) {
                // 展示层注入的再缓冲保留最后位置；读取发生在协程中，不订阅父组合。
                if (currentFrozen.value()) {
                    delay(200)
                } else if (playing) withFrameMillis {
                    // 等待帧期间也可能进入冻结，提交位置前再次确认。
                    if (!currentFrozen.value()) state.updatePosition(currentReadPosition.value())
                } else {
                    // A paused history restore or seek can finish after the first composition.
                    delay(200)
                    if (!currentFrozen.value()) state.updatePosition(currentReadPosition.value())
                }
            }
        }
        onPauseOrDispose { job.cancel() }
    }
}
