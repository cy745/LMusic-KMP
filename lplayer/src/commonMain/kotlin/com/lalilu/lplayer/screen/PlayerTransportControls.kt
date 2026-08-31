package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.components.ClickPart
import com.lalilu.lplayer.components.SeekbarLayout
import com.lalilu.lplayer.components.SeekbarPositionState
import com.lalilu.lplayer.extensions.PlayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 手机和大屏播放页共用的播放进度、切歌、播放模式交互。 */
@Composable
internal fun PlayerTransportControls(
    modifier: Modifier = Modifier,
    currentTime: MutableLongState,
    duration: State<Long>,
    positionState: SeekbarPositionState,
    animateColor: () -> Color,
    onDragStart: suspend (Offset) -> Unit = {},
    onDragStop: suspend (Int) -> Unit = {},
    onDispatchDragOffset: (Float) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    SeekbarLayout(
        modifier = modifier,
        animateColor = animateColor,
        positionState = positionState,
        maxValue = { duration.value.toFloat() },
        dataValue = { currentTime.longValue.toFloat() },
        onDragStart = onDragStart,
        onDragStop = onDragStop,
        onDispatchDragOffset = onDispatchDragOffset,
        onSeekTo = { position ->
            Logger.i("seekTo: $position")
            // Seek 是异步命令；先更新本地时钟，避免松手后短暂回弹到旧位置。
            currentTime.longValue = position.toLong()
            PlayerAction.SeekTo(position.toLong()).action()
        },
        onSwitchTo = { index ->
            PlayerAction.SetPlayMode(PlayMode.indexOf(index)).action()
        },
        onClick = { part ->
            scope.launch(Dispatchers.io) {
                when (part) {
                    ClickPart.Start -> LPlayer.instance.skipToPrevious()
                    ClickPart.Middle -> LPlayer.instance.togglePlayPause()
                    ClickPart.End -> LPlayer.instance.skipToNext()
                }
            }
        },
    )
}
