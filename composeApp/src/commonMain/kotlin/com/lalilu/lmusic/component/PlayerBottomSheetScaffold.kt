package com.lalilu.lmusic.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lalilu.component.BottomSheetScaffold
import com.lalilu.component.BottomSheetState
import com.lalilu.component.BottomSheetValue
import com.lalilu.component.rememberBottomSheetScaffoldState
import com.lalilu.extensions.ClassicBackHandler
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.lmusic.screen.PlayingInfoCard
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PlayerBottomSheetScaffold(
    modifier: Modifier,
    bottomBarModifier: Modifier,
    bottomSheetState: BottomSheetState,
    playerContent: @Composable ColumnScope.() -> Unit,
    mainContent: @Composable (PaddingValues) -> Unit,
    smartBarContent: @Composable (Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navigatorBar = WindowInsets.navigationBars.asPaddingValues()
    val ime = WindowInsets.ime.asPaddingValues()
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState)

    BottomSheetScaffold(
        modifier = modifier.fillMaxSize(),
        scaffoldState = bottomSheetScaffoldState,
        backgroundColor = Color.Transparent,
        sheetBackgroundColor = Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        sheetPeekHeight = 0.dp,
        sheetContent = playerContent,
        sheetShape = RectangleShape,
        content = { paddingValues ->
            PassThroughHelper.Passthrough(
                "SmartBarHeight" to {
                    72.dp + ime.calculateBottomPadding()
                        .coerceAtLeast(navigatorBar.calculateBottomPadding())
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    mainContent.invoke(paddingValues)

                    val draggable2DState = rememberDraggableState(onDelta = {
                        bottomSheetState.anchoredDraggableState.dispatchRawDelta(it)
                    })

                    Row(
                        modifier = bottomBarModifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer {
                                val progress = bottomSheetState.progress(
                                    BottomSheetValue.Collapsed,
                                    BottomSheetValue.Expanded
                                )
                                alpha = (1f - progress)
                            }
                            .fillMaxWidth()
                            .height(72.dp + navigatorBar.calculateBottomPadding()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayingInfoCardImpl(
                            modifier = Modifier
                                .draggable(
                                    state = draggable2DState,
                                    orientation = Orientation.Vertical,
                                    onDragStarted = {},
                                    onDragStopped = { bottomSheetState.anchoredDraggableState.settle(it) }
                                ).navigationBarsPadding(),
                            onClick = { scope.launch { bottomSheetState.expand() } },
                        )

                        smartBarContent.invoke(
                            Modifier
                                .weight(1f)
                                .draggable(
                                    state = draggable2DState,
                                    orientation = Orientation.Vertical,
                                    onDragStarted = {},
                                    onDragStopped = { bottomSheetState.anchoredDraggableState.settle(it) }
                                )
                        )
                    }
                }
            }
        }
    )

    // 监听用户的返回操作
    ClassicBackHandler(enabled = bottomSheetState.isExpanded) {
        scope.launch { bottomSheetState.collapse() }
    }
}


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

@Composable
fun PlayerBottomSheetContent(
    modifier: Modifier = Modifier,
    bottomSheetState: BottomSheetState,
    playerScreen: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
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
}