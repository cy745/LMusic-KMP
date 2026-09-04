package com.lalilu.lmusic.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import com.lalilu.lmusic.component.impl.PlayingInfoCardImpl
import com.lalilu.navigation.smartbar.SmartBarContentHeight
import kotlinx.coroutines.launch

@Composable
fun PlayerBottomSheetScaffold(
    modifier: Modifier,
    bottomBarModifier: Modifier,
    bottomSheetState: BottomSheetState,
    playerContent: @Composable ColumnScope.() -> Unit,
    mainContent: @Composable (PaddingValues) -> Unit,
    smartBarContent: @Composable (Modifier, Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navigatorBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = { SmartBarContentHeight + navigatorBar.calculateBottomPadding() }
    val navigationBarPadding = PaddingValues(
        bottom = navigatorBar.calculateBottomPadding()
    )
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
            Box(modifier = Modifier.fillMaxSize()) {
                PassThroughHelper.Passthrough(
                    "SmartBarHeight" to smartBarHeight
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 页面仍可绘制到 SmartBar 下方；其列表通过 SmartBarHeight
                            // 留出滚动末端。这里只从 IME 避让量中扣除系统导航栏高度，
                            // SmartBar 本体仍由 IME 避让，避免列表末项停在 SmartBar 后方。
                            .consumeWindowInsets(navigationBarPadding)
                            .imePadding()
                    ) {
                        mainContent.invoke(paddingValues)
                    }
                }

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
                        .wrapContentHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val draggable2DState = rememberDraggableState(onDelta = {
                        bottomSheetState.anchoredDraggableState.dispatchRawDelta(it)
                    })

                    PlayingInfoCardImpl(
                        modifier = Modifier
                            .draggable(
                                state = draggable2DState,
                                orientation = Orientation.Vertical,
                                onDragStarted = {},
                                onDragStopped = { bottomSheetState.anchoredDraggableState.settle(it) }
                            )
                            .navigationBarsPadding()
                            .height(SmartBarContentHeight),
                        onClick = { scope.launch { bottomSheetState.expand() } },
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        smartBarContent.invoke(
                            Modifier,
                            Modifier.draggable(
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
