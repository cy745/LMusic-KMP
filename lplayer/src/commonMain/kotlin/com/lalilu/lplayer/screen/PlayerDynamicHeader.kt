package com.lalilu.lplayer.screen

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import com.lalilu.llyricview.LyricContent
import com.lalilu.lplayer.components.BlurBackground
import com.lalilu.lplayer.components.PlayerScaffoldScope
import com.lalilu.lplayer.components.SeekbarPositionState
import kotlin.math.pow

@Composable
internal fun PlayerDynamicHeader(
    modifier: Modifier = Modifier,
    scaffold: PlayerScaffoldScope,
    timeline: SeekbarPositionState,
    backgroundColor: State<Color>,
    coverData: () -> Any?,
    currentTime: () -> Long,
    sampledPlaybackKey: () -> Any?,
    lyricContent: State<LyricContent>,
    onSeedColorChanged: (Color) -> Unit,
    onManualLyricsScrollingChanged: (Boolean) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .drawBehind { drawRect(color = backgroundColor.value) },
    ) {
        val decelerate: (Float) -> Float = remember {
            { value -> 1f - (1f - value) * (1f - value) }
        }
        val arcTranslation: (Float) -> Float = remember {
            { value -> -2f * (value - 0.5f).pow(2) + 0.5f }
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
                        val middleToMax = scaffold.middleToMaxProgress
                        val minToMiddle = scaffold.minToMiddleProgress
                        val minOffset = lerp(-size.width / 2f, 0f, decelerate(minToMiddle))
                        val maxOffset = lerp(
                            0f,
                            (constraints.maxHeight - constraints.maxWidth) / 2f,
                            decelerate(middleToMax),
                        )
                        val layoutCompensation = constraints.maxHeight - scaffold.position
                        val gestureEmphasis = arcTranslation(middleToMax) * 200f
                        val coverScale = lerp(
                            1f,
                            constraints.maxHeight.toFloat() / constraints.maxWidth.toFloat(),
                            middleToMax,
                        )

                        translationY = minOffset + maxOffset + layoutCompensation + gestureEmphasis
                        alpha = scaffold.minToMiddleProgress
                        scaleX = coverScale
                        scaleY = coverScale
                    },
                blurProgress = { scaffold.middleToMaxProgress },
                onColorPairFetched = { color, _ -> onSeedColorChanged(color) },
                imageData = { cover },
            )
        }

        PlayerLyricsPane(
            modifier = Modifier.fillMaxSize(),
            scaffold = scaffold,
            timeline = timeline,
            currentTime = currentTime,
            sampledPlaybackKey = sampledPlaybackKey,
            lyricContent = lyricContent,
            screenConstraints = constraints,
            onManualScrollingChanged = onManualLyricsScrollingChanged,
        )
    }
}
