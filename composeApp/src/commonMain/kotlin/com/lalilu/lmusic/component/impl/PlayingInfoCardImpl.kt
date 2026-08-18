package com.lalilu.lmusic.component.impl


import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lalilu.lmusic.component.PlayingInfoCard
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


@Composable
fun PlayingInfoCardImpl(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val currentPlaying = remember { LPlayer.instance.queue.currentItemFlow() }
        .collectAsState(null)
    val isPlaying = LPlayer.instance.isPlaying.collectAsState(false)
    val hasNext = LPlayer.instance.canSkipNext.collectAsState(false)
    val currentDuration = LPlayer.instance.currentDuration.collectAsState(0L)
    val currentPosition = remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis {
                currentPosition.value = runCatching { LPlayer.instance.currentPosition() }
                    .getOrElse { 0L }
            }
        }
    }

    PlayingInfoCard(
        modifier = modifier,
        currentPlaying = { currentPlaying.value },
        currentProgress = {
            (currentPosition.value / currentDuration.value.toFloat()).coerceIn(0f, 1f)
        },
        isPlaying = { isPlaying.value },
        hasNext = { hasNext.value },
        onClickPlayPause = { scope.launch { LPlayer.instance.togglePlayPause() } },
        onClickNext = { scope.launch { LPlayer.instance.skipToNext() } },
        onClick = onClick,
    )
}