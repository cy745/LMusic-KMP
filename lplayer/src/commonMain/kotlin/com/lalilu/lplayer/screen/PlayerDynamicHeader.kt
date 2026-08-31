package com.lalilu.lplayer.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.LyricLayout
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.components.BlurBackground
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
            imageData = { coverData() ?: "" },
        )

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
                    alpha = lyricAlpha
                },
            currentTime = {
                state.seekbarPositionState.positionFor(currentTime().toFloat()).toLong()
            },
            screenConstraints = constraints,
            lyricEntry = lyricEntry,
            isUserClickEnable = { true },
            isUserScrollEnable = { false },
            onItemClick = { PlayerAction.SeekTo(it.time).action() },
            onItemLongClick = {},
        )
    }
}
