package com.lalilu.lplayer.screen

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.components.CustomAnchoredDraggableState
import com.lalilu.lplayer.components.DragAnchor
import com.lalilu.lplayer.components.SeekbarPositionState
import com.lalilu.lplayer.components.rememberCustomAnchoredDraggableState
import com.lalilu.lplayer.components.rememberSeekbarPositionState
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 播放页自身的交互状态，不承载歌曲、歌词等业务数据。 */
@Stable
internal class PlayerScreenState(
    val draggable: CustomAnchoredDraggableState,
    val lyricScrollEnabled: MutableState<Boolean>,
    val lyricGestureInProgress: MutableState<Boolean>,
    val lyricInputUnlocked: MutableState<Boolean>,
    val lyricPreparationRequested: MutableState<Boolean>,
    val lyricDisplayReady: MutableState<Boolean>,
    val lyricListState: LazyListState,
    val playlistState: LazyListState,
    val seekbarPositionState: SeekbarPositionState,
    val middleToMaxProgress: State<Float>,
    val middleToMinProgress: State<Float>,
    val minToMiddleProgress: State<Float>,
)

@Composable
internal fun rememberPlayerScreenState(
    initialPlaybackPosition: () -> Float,
): PlayerScreenState {
    val haptic = LocalHapticFeedback.current
    val lyricScrollEnabled = remember { mutableStateOf(false) }
    val lyricGestureInProgress = remember { mutableStateOf(false) }
    val lyricInputUnlocked = rememberSaveable { mutableStateOf(false) }
    val lyricPreparationRequested = remember { mutableStateOf(false) }
    val lyricDisplayReady = remember { mutableStateOf(false) }
    val draggable = rememberCustomAnchoredDraggableState(
        onStateChange = { oldState, newState ->
            if (newState == DragAnchor.MiddleXMax && oldState != DragAnchor.MiddleXMax) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (newState != DragAnchor.Max) {
                lyricScrollEnabled.value = false
            }
            if (newState == DragAnchor.Middle) {
                lyricInputUnlocked.value = false
            }
        },
        onSettleTargetSelected = { target ->
            if (target == DragAnchor.Max) {
                lyricInputUnlocked.value = true
            }
        },
    )
    val lyricListState = rememberLazyListState()
    val playlistState = rememberLazyListState()
    val seekbarPositionState = rememberSeekbarPositionState(initialPlaybackPosition())

    val middleToMaxProgress = remember(draggable) {
        derivedStateOf {
            draggable.progressBetween(
                from = DragAnchor.Middle,
                to = DragAnchor.Max,
                offset = draggable.position.floatValue,
            )
        }
    }
    val middleToMinProgress = remember(draggable) {
        derivedStateOf {
            draggable.progressBetween(
                from = DragAnchor.Middle,
                to = DragAnchor.Min,
                offset = draggable.position.floatValue,
            )
        }
    }
    val minToMiddleProgress = remember(draggable) {
        derivedStateOf {
            draggable.progressBetween(
                from = DragAnchor.Min,
                to = DragAnchor.Middle,
                offset = draggable.position.floatValue,
            )
        }
    }

    return remember(
        draggable,
        lyricScrollEnabled,
        lyricGestureInProgress,
        lyricInputUnlocked,
        lyricPreparationRequested,
        lyricDisplayReady,
        lyricListState,
        playlistState,
        seekbarPositionState,
        middleToMaxProgress,
        middleToMinProgress,
        minToMiddleProgress,
    ) {
        PlayerScreenState(
            draggable = draggable,
            lyricScrollEnabled = lyricScrollEnabled,
            lyricGestureInProgress = lyricGestureInProgress,
            lyricInputUnlocked = lyricInputUnlocked,
            lyricPreparationRequested = lyricPreparationRequested,
            lyricDisplayReady = lyricDisplayReady,
            lyricListState = lyricListState,
            playlistState = playlistState,
            seekbarPositionState = seekbarPositionState,
            middleToMaxProgress = middleToMaxProgress,
            middleToMinProgress = middleToMinProgress,
            minToMiddleProgress = minToMiddleProgress,
        )
    }
}

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
