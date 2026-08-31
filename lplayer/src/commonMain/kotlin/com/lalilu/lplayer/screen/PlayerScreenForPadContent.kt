package com.lalilu.lplayer.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lalilu.LocalSeedColor
import com.lalilu.llyric.LyricItem
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.components.BlurBackground
import com.lalilu.lplayer.components.rememberSeekbarPositionState
import kotlinx.coroutines.flow.Flow


@Composable
internal fun PlayerScreenForPadContent(
    modifier: Modifier = Modifier,
    currentItem: State<LAudio?>,
    coverData: () -> Any?,
    currentTime: MutableLongState,
    duration: State<Long>,
    isPlaying: State<Boolean>,
    lyricEntry: State<List<LyricItem>>,
    queue: Flow<List<LAudio>>,
) {
    val seedColor = LocalSeedColor.current
    val playlistState = rememberLazyListState()
    val positionState = rememberSeekbarPositionState(currentTime.longValue.toFloat())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        PadPlayerBackground(
            coverData = coverData,
            onSeedColorChanged = { seedColor.value = it },
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1400.dp)
                .padding(start = 32.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PadNowPlayingPanel(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.9f),
                coverData = coverData,
                currentItem = currentItem,
                currentTime = currentTime,
                duration = duration,
                isPlaying = isPlaying,
                positionState = positionState,
            )

            PadContentPanel(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.1f),
                currentTime = {
                    positionState.positionFor(currentTime.longValue.toFloat()).toLong()
                },
                lyricEntry = lyricEntry,
                queue = queue,
                playlistState = playlistState,
            )
        }
    }
}

@Composable
private fun PadPlayerBackground(
    coverData: () -> Any?,
    onSeedColorChanged: (Color) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val cover = coverData()
        if (cover != null) {
            BlurBackground(
                modifier = Modifier.fillMaxSize(),
                imageData = { cover },
                onColorPairFetched = { color, _ -> onSeedColorChanged(color) },
                blurProgress = { 1f },
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f)),
        )
    }
}
