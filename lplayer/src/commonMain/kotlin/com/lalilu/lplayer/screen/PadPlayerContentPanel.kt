package com.lalilu.lplayer.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.LyricLayout
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.components.PlaylistLayout
import com.lalilu.lplayer.lplayer.generated.resources.Res
import com.lalilu.lplayer.lplayer.generated.resources.player_pane_lyrics
import com.lalilu.lplayer.lplayer.generated.resources.player_pane_queue
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PadContentPanel(
    modifier: Modifier,
    selectedPane: PadPlayerPane,
    onPaneSelected: (PadPlayerPane) -> Unit,
    currentTime: () -> Long,
    lyricEntry: State<List<LyricItem>>,
    queue: Flow<List<LAudio>>,
    playlistState: LazyListState,
) {
    val onUnselectedPane = if (selectedPane == PadPlayerPane.Lyrics) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = if (selectedPane == PadPlayerPane.Lyrics) {
            Color.Black.copy(alpha = 0.36f)
        } else {
            MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
        },
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PadPaneSelector(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                selectedPane = selectedPane,
                onUnselectedPane = onUnselectedPane,
                onPaneSelected = onPaneSelected,
            )

            AnimatedContent(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                targetState = selectedPane,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "PadPlayerPane",
            ) { pane ->
                when (pane) {
                    PadPlayerPane.Lyrics -> LyricPanel(
                        modifier = Modifier.fillMaxSize(),
                        currentTime = currentTime,
                        lyricEntry = lyricEntry,
                    )

                    PadPlayerPane.Queue -> PlaylistLayout(
                        modifier = Modifier.fillMaxSize(),
                        listState = playlistState,
                        contentPadding = PaddingValues(bottom = 16.dp),
                        items = queue,
                    )
                }
            }
        }
    }
}

@Composable
private fun PadPaneSelector(
    modifier: Modifier = Modifier,
    selectedPane: PadPlayerPane,
    onUnselectedPane: Color,
    onPaneSelected: (PadPlayerPane) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PadPaneButton(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.player_pane_lyrics),
            selected = selectedPane == PadPlayerPane.Lyrics,
            unselectedContentColor = onUnselectedPane,
            onClick = { onPaneSelected(PadPlayerPane.Lyrics) },
        )
        PadPaneButton(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.player_pane_queue),
            selected = selectedPane == PadPlayerPane.Queue,
            unselectedContentColor = onUnselectedPane,
            onClick = { onPaneSelected(PadPlayerPane.Queue) },
        )
    }
}

@Composable
private fun PadPaneButton(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    unselectedContentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else Color.Transparent,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else unselectedContentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
internal fun LyricPanel(
    modifier: Modifier = Modifier,
    currentTime: () -> Long,
    lyricEntry: State<List<LyricItem>>,
) {
    BoxWithConstraints(modifier = modifier) {
        LyricLayout(
            modifier = Modifier.fillMaxSize(),
            currentTime = currentTime,
            screenConstraints = constraints,
            lyricEntry = lyricEntry,
            isUserClickEnable = { true },
            isUserScrollEnable = { true },
            onItemClick = { PlayerAction.SeekTo(it.time).action() },
            onItemLongClick = {},
        )
    }
}
