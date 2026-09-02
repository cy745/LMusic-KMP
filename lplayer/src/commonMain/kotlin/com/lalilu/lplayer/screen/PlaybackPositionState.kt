package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 只在页面处于 Resume 状态且正在播放时逐帧读取播放器位置。
 * 高频位置保存在独立 State 中，避免并入低频页面模型后带动整页重组。
 */
@Composable
internal fun rememberPlaybackPositionState(
    isPlaying: Boolean,
    playbackKey: Any?,
): MutableLongState {
    val scope = rememberCoroutineScope()
    val position = remember { mutableLongStateOf(LPlayer.instance.currentPosition()) }

    LifecycleResumeEffect(isPlaying, playbackKey) {
        val job = scope.launch {
            position.longValue = LPlayer.instance.currentPosition()
            while (isActive && isPlaying) {
                withFrameMillis {
                    position.longValue = LPlayer.instance.currentPosition()
                }
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    return position
}
