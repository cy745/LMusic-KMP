package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.components.ClickPart
import com.lalilu.lplayer.components.SeekbarLayout
import com.lalilu.lplayer.extensions.PlayMode

/** 手机和大屏播放页共用的播放进度、切歌、播放模式交互。 */
@Composable
internal fun PlayerTransportControls(
    modifier: Modifier = Modifier,
    timeline: PlaybackTimelineState,
    animateColor: () -> Color,
    onDragStart: suspend (Offset) -> Unit = {},
    onDragStop: suspend (Int) -> Unit = {},
    onDispatchDragOffset: (Float) -> Unit = {},
) {
    SeekbarLayout(
        modifier = modifier,
        animateColor = animateColor,
        positionMs = { timeline.positionMs },
        durationMs = { timeline.durationMs },
        progress = { timeline.progress },
        onSeekStart = timeline::onDragStart,
        onSeekDrag = timeline::onDrag,
        onSeekCancel = timeline::onDragCancel,
        onSeek = { position ->
            Logger.i("seekTo: $position")
            // 时间轴会自己把显示值钉在目标位置并等待引擎落地，这里只负责下发命令。
            timeline.onSeek(position)
            PlayerAction.SeekTo(position).action()
        },
        onDragStart = onDragStart,
        onDragStop = onDragStop,
        onDispatchDragOffset = onDispatchDragOffset,
        onSwitchTo = { index ->
            PlayerAction.SetPlayMode(PlayMode.indexOf(index)).action()
        },
        onClick = { part ->
            when (part) {
                ClickPart.Start -> PlayerAction.SkipToPrevious
                ClickPart.Middle -> PlayerAction.PlayOrPause
                ClickPart.End -> PlayerAction.SkipToNext
            }.action()
        },
    )
}
