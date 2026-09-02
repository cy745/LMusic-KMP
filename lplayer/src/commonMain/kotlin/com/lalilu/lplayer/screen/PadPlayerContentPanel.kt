package com.lalilu.lplayer.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lalilu.llyricview.LyricContent
import com.lalilu.llyricview.LyricLayout
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.components.PlaylistLayout
import kotlinx.coroutines.flow.Flow

/**
 * 抽屉双锚点：
 * - [Lyrics]：歌词全屏（偏移 0，默认态）
 * - [Queue]：歌单全屏（向左平移一个容器宽度）
 */
private enum class PadDrawerAnchor {
    Lyrics,
    Queue,
}

@Composable
internal fun PadContentPanel(
    modifier: Modifier,
    currentTime: () -> Long,
    sampledPlaybackKey: () -> Any?,
    lyricContent: State<LyricContent>,
    queue: Flow<List<LAudio>>,
    playlistState: LazyListState,
) {
    BoxWithConstraints(modifier = modifier) {
        val constraints = constraints
        // 歌词与歌单共用同一份拖拽状态，手势统一挂在父容器上：
        // 任意位置的水平滑动都能驱动抽屉（含快速 fling），不会在两层之间串手势。
        val anchors = remember(maxWidth) {
            DraggableAnchors {
                PadDrawerAnchor.Lyrics at 0f
                PadDrawerAnchor.Queue at -constraints.maxWidth.toFloat()
            }
        }
        val drawerState = remember {
            AnchoredDraggableState(
                initialValue = PadDrawerAnchor.Lyrics,
                anchors = anchors,
            )
        }

        Box(
            modifier = modifier.fillMaxSize()
                .anchoredDraggable(
                    state = drawerState,
                    reverseDirection = false,
                    orientation = Orientation.Horizontal
                ),
            contentAlignment = Alignment.Center
        ) {
            // 歌词层：默认全屏，随抽屉滑出渐隐并轻微左移，形成联动视差
            LyricPanel(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = drawerState.progress(from = PadDrawerAnchor.Lyrics, to = PadDrawerAnchor.Queue)
                        alpha = 1f - (progress * 1.5f)
                    },
                constraints = constraints,
                currentTime = currentTime,
                sampledPlaybackKey = sampledPlaybackKey,
                lyricContent = lyricContent,
            )

            Spacer(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(56.dp)
                    .width(4.dp)
                    .graphicsLayer {
                        val offset = drawerState.anchors.positionOf(PadDrawerAnchor.Lyrics) - drawerState.offset
                        translationX = -offset
                        alpha = 1f - (offset / 36.dp.toPx()).coerceIn(0f..1f)
                    }
                    .background(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50)
                    )
            )

            // 歌单层：初始位于容器右缘外，随拖拽滑入（水平手势由父级统一处理）
            PlaylistPanel(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = size.width + drawerState.offset },
                queue = queue,
                playlistState = playlistState,
            )
        }
    }
}

@Composable
internal fun LyricPanel(
    modifier: Modifier = Modifier,
    currentTime: () -> Long,
    sampledPlaybackKey: () -> Any?,
    constraints: Constraints,
    lyricContent: State<LyricContent>,
) {
    LyricLayout(
        modifier = modifier.fillMaxSize(),
        currentTime = currentTime,
        sampledPlaybackKey = sampledPlaybackKey,
        screenConstraints = constraints,
        lyricContent = lyricContent,
        isUserClickEnable = { true },
        isUserScrollEnable = { false },
        onItemClick = { PlayerAction.SeekTo(it.time).action() },
        onItemLongClick = {},
    )
}

@Composable
internal fun PlaylistPanel(
    modifier: Modifier = Modifier,
    queue: Flow<List<LAudio>>,
    playlistState: LazyListState,
) {
    val contentPadding = WindowInsets.statusBars.asPaddingValues() +
            WindowInsets.navigationBars.asPaddingValues() +
            PaddingValues(vertical = 16.dp)

    PlaylistLayout(
        modifier = modifier.fillMaxSize(),
        listState = playlistState,
        contentPadding = contentPadding,
        items = queue,
    )
}
