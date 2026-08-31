package com.lalilu.lplayer.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowWidthSizeClass
import coil3.compose.AsyncImage
import com.lalilu.adaptive
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.components.PlayingToolbar
import com.lalilu.lplayer.components.SeekbarPositionState

@Composable
internal fun PadNowPlayingPanel(
    modifier: Modifier,
    coverData: () -> Any?,
    currentItem: State<LAudio?>,
    currentTime: MutableLongState,
    duration: State<Long>,
    isPlaying: State<Boolean>,
    positionState: SeekbarPositionState,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isGreaterThanMedium = windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val padding = WindowInsets.statusBars.asPaddingValues() +
            WindowInsets.navigationBars.asPaddingValues() +
            (if (isGreaterThanMedium) PaddingValues() else WindowInsets.displayCutout.asPaddingValues())

    Column(
        modifier = modifier.padding(padding),
        verticalArrangement = Arrangement.spacedBy(space = 16.dp, alignment = Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            modifier = Modifier.then(
                if (isGreaterThanMedium) Modifier.fillMaxWidth()
                else Modifier.weight(1f).aspectRatio(1f).align(Alignment.Start)
            ),
            targetState = coverData(),
            transitionSpec = {
                (fadeIn(tween(500)) togetherWith ExitTransition.KeepUntilTransitionsFinished)
                    .apply { targetContentZIndex = 1f }
            },
            label = "PadPlayerCover",
        ) { model ->
            Card(
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = model,
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                )
            }
        }

        if (isGreaterThanMedium) {
            PlayingToolbar(
                modifier = Modifier.fillMaxWidth(),
                title = { currentItem.value?.title ?: "LMusic" },
                subtitle = { currentItem.value?.subtitle ?: "....." },
                contentColor = { Color.White },
                isPlaying = { isPlaying.value },
                contentPadding = PaddingValues(0.dp),
            )
        }

        PlayerTransportControls(
            modifier = Modifier.fillMaxWidth()
                .padding(bottom = 16.dp),
            currentTime = currentTime,
            duration = duration,
            positionState = positionState,
            animateColor = { accentColor },
        )
    }
}