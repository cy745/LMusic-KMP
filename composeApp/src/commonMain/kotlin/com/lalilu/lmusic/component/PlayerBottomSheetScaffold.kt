package com.lalilu.lmusic.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetState
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.lalilu.extensions.ClassicBackHandler
import com.lalilu.lmusic.screen.PlayingInfoCard
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PlayerBottomSheetScaffold(
    modifier: Modifier,
    bottomSheetState: BottomSheetState,
    playerContent: @Composable ColumnScope.() -> Unit,
    mainContent: @Composable (PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navigatorBar = WindowInsets.navigationBars.asPaddingValues()
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState)

    BottomSheetScaffold(
        modifier = modifier.fillMaxSize(),
        scaffoldState = bottomSheetScaffoldState,
        backgroundColor = Color.Transparent,
        sheetBackgroundColor = Color.Transparent,
        sheetPeekHeight = 72.dp + navigatorBar.calculateBottomPadding(),
        sheetContent = playerContent,
        content = mainContent
    )

    if (!LocalInspectionMode.current) {
        // 监听用户的返回操作
        ClassicBackHandler(enabled = bottomSheetState.isExpanded) {
            scope.launch { bottomSheetState.collapse() }
        }
    }
}

@Composable
fun PlayerBottomSheetContent(
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier,
    bottomSheetState: BottomSheetState,
    playerScreen: @Composable BoxScope.() -> Unit,
    smartBarContent: @Composable (Modifier) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val navigatorBar = WindowInsets.navigationBars.asPaddingValues()

    val currentPlaying = LPlayer.instance.currentItem.collectAsState(null)
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = bottomSheetState.progress(
                        BottomSheetValue.Collapsed,
                        BottomSheetValue.Expanded
                    )
                    alpha = progress
                }
        ) {
            playerScreen.invoke(this)
        }

        Row(
            modifier = bottomBarModifier
                .graphicsLayer {
                    val progress = bottomSheetState.progress(
                        BottomSheetValue.Collapsed,
                        BottomSheetValue.Expanded
                    )

                    translationY = constraints.maxHeight * progress
                    alpha = (1f - progress)
                }
                .fillMaxWidth()
                .background(color = MaterialTheme.colorScheme.background.copy(0.6f))
                .height(72.dp + navigatorBar.calculateBottomPadding()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayingInfoCard(
                modifier = Modifier.navigationBarsPadding(),
                currentPlaying = { currentPlaying.value },
                currentProgress = {
                    (currentPosition.value / currentDuration.value.toFloat()).coerceIn(0f, 1f)
                },
                isPlaying = { isPlaying.value },
                hasNext = { hasNext.value },
                onClickPlayPause = { scope.launch { LPlayer.instance.togglePlayPause() } },
                onClickNext = { scope.launch { LPlayer.instance.skipToNext() } },
                onClick = { scope.launch { bottomSheetState.expand() } },
            )

            smartBarContent.invoke(Modifier.weight(1f))
        }
    }
}