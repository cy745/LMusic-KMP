package com.lalilu.lplayer.screen

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.lerp
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.LyricLayout
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.components.BlurBackground
import com.lalilu.lplayer.components.DragAnchor
import com.lalilu.lplayer.components.LyricGestureOverlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

@Composable
internal fun PlayerDynamicHeader(
    modifier: Modifier = Modifier,
    state: PlayerScreenState,
    backgroundColor: State<Color>,
    coverData: () -> Any?,
    currentTime: () -> Long,
    lyricEntry: State<List<LyricItem>>,
    onSeedColorChanged: (Color) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .drawBehind { drawRect(color = backgroundColor.value) },
    ) {
        val accelerateDecelerate: (Float) -> Float = remember {
            { value -> ((cos((value + 1) * PI) / 2.0f) + 0.5f).toFloat() }
        }
        val decelerate: (Float) -> Float = remember {
            { value -> 1f - (1f - value) * (1f - value) }
        }
        val arcTranslation: (Float) -> Float = remember {
            { value -> -2f * (value - 0.5f).pow(2) + 0.5f }
        }
        val shouldComposeLyrics by remember(state) {
            derivedStateOf {
                state.middleToMaxProgress.value > 0f ||
                        state.lyricPreparationRequested.value ||
                        state.lyricGestureInProgress.value
            }
        }
        val isLyricInputBlocked by remember(state) {
            derivedStateOf {
                !state.lyricInputUnlocked.value
            }
        }

        val cover = coverData()
        if (cover == null) {
            Spacer(modifier = Modifier.fillMaxSize())
        } else {
            BlurBackground(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        val middleToMax = state.middleToMaxProgress.value
                        val minToMiddle = state.minToMiddleProgress.value
                        val minOffset = lerp(-size.width / 2f, 0f, decelerate(minToMiddle))
                        val maxOffset = lerp(
                            0f,
                            (constraints.maxHeight - constraints.maxWidth) / 2f,
                            decelerate(middleToMax),
                        )
                        val layoutCompensation = constraints.maxHeight - state.draggable.position.floatValue
                        val gestureEmphasis = arcTranslation(middleToMax) * 200f
                        val coverScale = lerp(
                            1f,
                            constraints.maxHeight.toFloat() / constraints.maxWidth.toFloat(),
                            middleToMax,
                        )

                        translationY = minOffset + maxOffset + layoutCompensation + gestureEmphasis
                        alpha = state.minToMiddleProgress.value
                        scaleX = coverScale
                        scaleY = coverScale
                    },
                blurProgress = { state.middleToMaxProgress.value },
                onColorPairFetched = { color, _ -> onSeedColorChanged(color) },
                imageData = { cover },
            )
        }

        ReusableLyricHost(
            active = shouldComposeLyrics,
            modifier = Modifier.fillMaxSize(),
        ) {
            LyricLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val middleToMax = state.middleToMaxProgress.value
                        val lyricAlpha = (2f * accelerateDecelerate(middleToMax) - 1f)
                            .coerceAtLeast(0f)
                        val layoutCompensation = size.height - state.draggable.position.floatValue
                        val gestureEmphasis = arcTranslation(middleToMax) * 600f

                        translationY = gestureEmphasis + layoutCompensation
                        alpha = if (state.lyricDisplayReady.value) lyricAlpha else 0f
                    }
                    .then(
                        if (isLyricInputBlocked) Modifier.clearAndSetSemantics { }
                        else Modifier
                    ),
                listState = state.lyricListState,
                currentTime = {
                    state.seekbarPositionState.positionFor(currentTime().toFloat()).toLong()
                },
                screenConstraints = constraints,
                lyricEntry = lyricEntry,
                prepareForDisplay = true,
                onDisplayReadyChanged = { state.lyricDisplayReady.value = it },
                isUserClickEnable = { true },
                isUserScrollEnable = { state.lyricScrollEnabled.value },
                onPositionReset = { state.lyricScrollEnabled.value = false },
                onItemClick = { PlayerAction.SeekTo(it.time).action() },
                onItemLongClick = {
                    if (!state.lyricScrollEnabled.value) {
                        state.lyricScrollEnabled.value = true
                    }
                },
            )
        }

        if (isLyricInputBlocked) {
            LyricGestureOverlay(
                modifier = Modifier.fillMaxSize(),
                draggable = state.draggable,
                onGestureProgress = {
                    val middle = state.draggable.getPositionByAnchor(DragAnchor.Middle)
                    if (middle != null && state.draggable.position.floatValue > middle) {
                        state.lyricPreparationRequested.value = true
                    }
                },
                onGestureStopped = {
                    state.lyricPreparationRequested.value = false
                },
            )
        }
    }
}

/**
 * 为停用的歌词节点提供一个始终活跃的测量边界。
 *
 * [ReusableContentHost] 会保留已经停用的 LayoutNode 以供下次复用；如果直接把这些节点放进
 * BoxWithConstraints，在共享元素的 Lookahead 测量中父布局仍可能尝试测量它们。这里由外层
 * Layout 在停用期间跳过子节点测量，重新激活后才恢复正常的测量和放置。
 */
@Composable
private fun ReusableLyricHost(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            ReusableContentHost(active = active) {
                ReusableContent(key = "PlayerLyrics", content = content)
            }
        },
    ) { measurables, constraints ->
        if (!active) {
            layout(constraints.maxWidth, constraints.maxHeight) { }
        } else {
            val placeables = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { it.place(0, 0) }
            }
        }
    }
}
