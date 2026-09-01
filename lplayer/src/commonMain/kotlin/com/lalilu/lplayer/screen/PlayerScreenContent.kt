package com.lalilu.lplayer.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.lalilu.llyric.LyricItem
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.components.DragAnchor
import com.lalilu.lplayer.components.NestedScrollBaseLayout
import com.lalilu.lplayer.components.PlaylistLayout
import com.lalilu.navigation.LocalModalBottomSheetState
import kotlinx.coroutines.flow.Flow

@Composable
internal fun PlayerScreenContent(
    state: PlayerScreenState,
    currentItem: State<LAudio?>,
    currentCover: () -> Any?,
    currentTime: MutableLongState,
    duration: State<Long>,
    isPlaying: State<Boolean>,
    lyricEntry: State<List<LyricItem>>,
    queue: Flow<List<LAudio>>,
    backgroundColor: State<Color>,
    onSeedColorChanged: (Color) -> Unit,
) {
    val density = LocalDensity.current
    val navigationBar = WindowInsets.navigationBars
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val scope = rememberCoroutineScope()

    NestedScrollBaseLayout(
        draggable = state.draggable,
        isLyricScrollEnable = state.lyricScrollEnabled,
        isLyricGestureInProgress = state.lyricGestureInProgress,
        toolbarContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 10.dp)
                    .graphicsLayer {
                        val middleToMax = state.middleToMaxProgress.value
                        translationY = lerp(
                            0f,
                            -navigationBar.getBottom(density).toFloat() + 10.dp.toPx(),
                            middleToMax,
                        )
                        alpha = (
                                1.25f * (middleToMax + state.middleToMinProgress.value) - 0.25f
                                ).coerceAtLeast(0f)
                    },
            ) {
                PlayerToolbarContent(
                    modifier = Modifier.fillMaxWidth(),
                    title = { currentItem.value?.title ?: "LMusic" },
                    subtitle = { currentItem.value?.subtitle ?: "....." },
                    contentColor = { contentColor },
                    isPlaying = { isPlaying.value },
                    isUserTouchEnabled = {
                        state.draggable.state.value == DragAnchor.Min ||
                                state.draggable.state.value == DragAnchor.Max
                    },
                    showExtraActions = { state.draggable.state.value == DragAnchor.Max },
                )
            }
        },
        dynamicHeaderContent = { headerModifier ->
            PlayerDynamicHeader(
                modifier = headerModifier,
                state = state,
                backgroundColor = backgroundColor,
                coverData = currentCover,
                currentTime = { currentTime.longValue },
                lyricEntry = lyricEntry,
                onSeedColorChanged = onSeedColorChanged,
            )
        },
        playlistContent = { playlistModifier ->
            PlaylistLayout(
                modifier = playlistModifier,
                listState = state.playlistState,
                items = queue,
            )
        },
        overlayContent = {
            val controlsProgress = animateFloatAsState(
                targetValue = if (!state.lyricScrollEnabled.value) 1f else 0f,
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
                        .padding(horizontal = 40.dp)
                        .padding(bottom = 100.dp),
                    currentTime = currentTime,
                    duration = duration,
                    positionState = state.seekbarPositionState,
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
