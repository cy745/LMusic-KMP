package com.lalilu.lplayer.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.lalilu.llyricview.LyricContent
import com.lalilu.llyricview.obtainLyricSettings
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.components.DragAnchor
import com.lalilu.lplayer.components.PlayerScaffold
import com.lalilu.lplayer.components.PlaylistLayout
import com.lalilu.lplayer.components.rememberSeekbarPositionState
import com.lalilu.lplayer.extensions.SystemBarsVisibilityEffect
import com.lalilu.lplayer.extensions.hideControl
import com.lalilu.navigation.LocalModalBottomSheetState
import kotlinx.coroutines.flow.Flow

@Composable
internal fun PlayerScreenContent(
    currentItem: State<LAudio?>,
    currentCover: () -> Any?,
    currentTime: MutableLongState,
    sampledPlaybackKey: () -> Any?,
    duration: State<Long>,
    isPlaying: State<Boolean>,
    lyricContent: State<LyricContent>,
    queue: Flow<List<LAudio>>,
    backgroundColor: State<Color>,
    onSeedColorChanged: (Color) -> Unit,
) {
    val density = LocalDensity.current
    val navigationBar = WindowInsets.navigationBars
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val timeline = rememberSeekbarPositionState {
        currentTime.longValue.toFloat()
    }
    var isManuallyScrollingLyrics by remember { mutableStateOf(false) }
    // 「歌词页展开时隐藏其他组件」与歌词设置页 / 播放页弹窗共用同一份设置
    val lyricSettings = remember { obtainLyricSettings() }
    PlayerScaffold(
        toolbarContent = {
            Column(
                modifier = Modifier
                    // 歌词页展开时隐藏其他组件：toolbar 需要"先点击一下显示，再点击才触发按钮"，
                    // 因此 intercept 为 true
                    .hideControl(
                        enable = {
                            lyricSettings.value.autoHideComponents &&
                                currentAnchor == DragAnchor.Max
                        },
                        intercept = { true },
                    )
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 10.dp)
                    .graphicsLayer {
                        val middleToMax = middleToMaxProgress
                        translationY = lerp(
                            0f,
                            -navigationBar.getBottom(density).toFloat() + 10.dp.toPx(),
                            middleToMax,
                        )
                        alpha = (1.25f * (middleToMax + middleToMinProgress) - 0.25f)
                            .coerceAtLeast(0f)
                    },
            ) {
                PlayerToolbarContent(
                    modifier = Modifier.fillMaxWidth(),
                    title = { currentItem.value?.title ?: "LMusic" },
                    subtitle = { currentItem.value?.subtitle ?: "....." },
                    contentColor = { contentColor },
                    isPlaying = { isPlaying.value },
                    isUserTouchEnabled = {
                        currentAnchor == DragAnchor.Min || currentAnchor == DragAnchor.Max
                    },
                    showExtraActions = { currentAnchor == DragAnchor.Max },
                    // 歌词页收起（非 Max 锚点）时，双击 toolbar 区域把播放列表滚回顶部
                    onDoubleClick = {
                        if (currentAnchor != DragAnchor.Max) scrollPlaylistToTop()
                    },
                )
            }
        },
        dynamicHeaderContent = {
            PlayerDynamicHeader(
                scaffold = this,
                timeline = timeline,
                backgroundColor = backgroundColor,
                coverData = currentCover,
                currentTime = { currentTime.longValue },
                sampledPlaybackKey = sampledPlaybackKey,
                lyricContent = lyricContent,
                onSeedColorChanged = onSeedColorChanged,
                onManualLyricsScrollingChanged = { isManuallyScrollingLyrics = it },
            )
        },
        playlistContent = { playlistModifier, listState ->
            PlaylistLayout(
                modifier = playlistModifier,
                listState = listState,
                items = queue,
            )
        },
        overlayContent = { scaffold ->
            // 歌词页展开且开启自动隐藏时，系统状态栏一并隐藏（桌面端 / Web 为空操作，见 actual 实现）
            SystemBarsVisibilityEffect(
                visible = !(lyricSettings.value.autoHideComponents && scaffold.currentAnchor == DragAnchor.Max),
            )

            val controlsProgress = animateFloatAsState(
                targetValue = if (!isManuallyScrollingLyrics) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "PlayerControlsVisibility",
            )
            val bottomSheetState = LocalModalBottomSheetState.current

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = controlsProgress.value
                        translationY = (1f - controlsProgress.value) * 500f
                    },
            ) {
                PlayerTransportControls(
                    modifier = Modifier
                        // 注意 padding 必须在 hideControl 之前：hideControl 会挂上指针监听，
                        // 放在 padding 之前会把下方留白也算进它的触摸区域（点留白会误触发进度条本身）。
                        .padding(horizontal = 40.dp)
                        .padding(bottom = 100.dp)
                        // 歌词页展开时隐藏其他组件：进度条不拦截点击（intercept 默认 false），
                        // 以保证其原有手势（拖动进度 / 点击切歌）不受影响
                        .hideControl(
                            enable = {
                                lyricSettings.value.autoHideComponents &&
                                    scaffold.currentAnchor == DragAnchor.Max
                            },
                        ),
                    currentTime = currentTime,
                    duration = duration,
                    positionState = timeline,
                    animateColor = { backgroundColor.value },
                    onDispatchDragOffset = { deltaY -> bottomSheetState.anchoredDraggableState.dispatchRawDelta(deltaY) },
                    onDragStop = { result ->
                        if (result == 0) {
                            bottomSheetState.anchoredDraggableState.settle(0f)
                        } else {
                            bottomSheetState.hide()
                        }
                    },
                )
            }
        },
    )
}
