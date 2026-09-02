package com.lalilu.lplayer.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * 歌词尚未开放交互时覆盖在动态头部上的透明输入层。
 *
 * 它与真实歌词列表相互独立：收起状态下即使歌词没有参与组合，用户仍然可以从相同区域
 * 拖动播放页；准备显示歌词期间，它也会持续占有本轮指针事件，避免点击或滑动穿透到列表。
 */
@Composable
fun LyricGestureOverlay(
    modifier: Modifier = Modifier,
    onGestureStarted: () -> Unit = {},
    onDrag: (delta: Float) -> Unit,
    onGestureStopped: (velocity: Float) -> Unit,
) {
    val currentOnGestureStarted = rememberUpdatedState(onGestureStarted)
    val currentOnDrag = rememberUpdatedState(onDrag)
    val currentOnGestureStopped = rememberUpdatedState(onGestureStopped)
    val dragState = rememberDraggableState { delta ->
        currentOnDrag.value(delta)
    }

    Spacer(
        modifier = modifier
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    currentOnGestureStarted.value()
                },
                onDragStopped = { velocity ->
                    currentOnGestureStopped.value(velocity)
                },
            )
    )
}
