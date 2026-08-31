package com.lalilu.lplayer.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lalilu.RemixIcon
import com.lalilu.common.ext.io
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.components.SeekbarPositionState
import com.lalilu.lplayer.extensions.PlayMode
import com.lalilu.lplayer.lplayer.generated.resources.Res
import com.lalilu.lplayer.lplayer.generated.resources.player_action_next
import com.lalilu.lplayer.lplayer.generated.resources.player_action_pause
import com.lalilu.lplayer.lplayer.generated.resources.player_action_play
import com.lalilu.lplayer.lplayer.generated.resources.player_action_previous
import com.lalilu.lplayer.lplayer.generated.resources.player_mode_list_recycle
import com.lalilu.lplayer.lplayer.generated.resources.player_mode_repeat_one
import com.lalilu.lplayer.lplayer.generated.resources.player_mode_shuffle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun PadNowPlayingPanel(
    modifier: Modifier,
    coverData: () -> Any?,
    currentTime: MutableLongState,
    duration: State<Long>,
    isPlaying: State<Boolean>,
    positionState: SeekbarPositionState,
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val coverSize = minOf(maxWidth, maxHeight)
            AnimatedContent(
                modifier = Modifier.size(coverSize),
                targetState = coverData(),
                transitionSpec = {
                    (fadeIn(tween(500)) togetherWith ExitTransition.KeepUntilTransitionsFinished)
                        .apply { targetContentZIndex = 1f }
                },
                label = "PadPlayerCover",
            ) { model ->
                Card(
                    modifier = Modifier.fillMaxSize(),
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
        }

        PlayerTransportControls(
            modifier = Modifier.fillMaxWidth(),
            currentTime = currentTime,
            duration = duration,
            positionState = positionState,
            animateColor = { accentColor },
        )

        PadPrimaryPlaybackControls(isPlaying = isPlaying)
    }
}

@Composable
private fun PadPrimaryPlaybackControls(
    isPlaying: State<Boolean>,
) {
    val scope = rememberCoroutineScope()
    val playModeName by LPlayerKV.playMode
    val playMode = PlayMode.from(playModeName)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                modifier = Modifier.size(56.dp),
                onClick = {
                    scope.launch(Dispatchers.io) { LPlayer.instance.skipToPrevious() }
                },
            ) {
                Icon(
                    modifier = Modifier.graphicsLayer { rotationZ = 180f },
                    imageVector = vectorResource(RemixIcon.Media.skipForwardLine),
                    contentDescription = stringResource(Res.string.player_action_previous),
                )
            }
            FilledIconButton(
                modifier = Modifier.size(64.dp),
                onClick = {
                    scope.launch(Dispatchers.io) { LPlayer.instance.togglePlayPause() }
                },
            ) {
                PlaybackGlyph(
                    modifier = Modifier.size(28.dp),
                    isPlaying = isPlaying.value,
                    contentDescription = stringResource(
                        if (isPlaying.value) Res.string.player_action_pause
                        else Res.string.player_action_play,
                    ),
                )
            }
            FilledTonalIconButton(
                modifier = Modifier.size(56.dp),
                onClick = {
                    scope.launch(Dispatchers.io) { LPlayer.instance.skipToNext() }
                },
            ) {
                Icon(
                    imageVector = vectorResource(RemixIcon.Media.skipForwardLine),
                    contentDescription = stringResource(Res.string.player_action_next),
                )
            }
        }

        Surface(
            onClick = {
                val next = PlayMode.entries[(playMode.ordinal + 1) % PlayMode.entries.size]
                PlayerAction.SetPlayMode(next).action()
            },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        ) {
            Text(
                text = stringResource(playMode.label),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private val PlayMode.label: StringResource
    get() = when (this) {
        PlayMode.ListRecycle -> Res.string.player_mode_list_recycle
        PlayMode.RepeatOne -> Res.string.player_mode_repeat_one
        PlayMode.Shuffle -> Res.string.player_mode_shuffle
    }

@Composable
private fun PlaybackGlyph(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    contentDescription: String,
) {
    val color = LocalContentColor.current
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        if (isPlaying) {
            val barWidth = size.width * 0.24f
            val gap = size.width * 0.18f
            val left = (size.width - barWidth * 2f - gap) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth * 0.25f),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(left + barWidth + gap, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth * 0.25f),
            )
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.18f, 0f)
                lineTo(size.width * 0.88f, size.height / 2f)
                lineTo(size.width * 0.18f, size.height)
                close()
            }
            drawPath(path = path, color = color)
        }
    }
}
