package com.lalilu.lmusic.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.lalilu.component.AnchoredDraggableState
import com.lalilu.component.ModalBottomSheetLayout
import com.lalilu.component.ModalBottomSheetState
import com.lalilu.component.ModalBottomSheetValue
import com.lalilu.navigation.LocalModalBottomSheetState
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ScaleBottomSheetLayout(
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier,
    bottomSheetState: ModalBottomSheetState,
    playerContent: @Composable (@Composable () -> Unit) -> Unit = {},
    mainContent: @Composable () -> Unit = {},
    smartBarContent: @Composable (Modifier) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val navigatorBar = WindowInsets.navigationBars.asPaddingValues()

    CompositionLocalProvider(LocalModalBottomSheetState provides bottomSheetState) {
        ModalBottomSheetLayout(
            modifier = modifier.fillMaxSize(),
            sheetState = bottomSheetState,
            sheetShape = RectangleShape,
            sheetContentColor = MaterialTheme.colorScheme.background,
            sheetContent = {
                Box(modifier = Modifier) {
                    mainContent.invoke()

                    Row(
                        modifier = bottomBarModifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(color = MaterialTheme.colorScheme.background.copy(0.6f))
                            .height(72.dp + navigatorBar.calculateBottomPadding())
                    ) {
                        smartBarContent.invoke(Modifier.fillMaxSize())
                    }
                }
            },
            content = {
                val scaleValue = remember(bottomSheetState) {
                    derivedStateOf {
                        val state = bottomSheetState.anchoredDraggableState
                        val min = state.anchors.minAnchor()
                        val max = state.anchors.maxAnchor()
                        val offset = state.offset

                        val fraction = offset.normalize(min, max)
                        val scale = 0.8f + 0.2f * fraction
                        scale.takeIf { !it.isNaN() } ?: 1f
                    }
                }
                Surface(color = Color.Black) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scaleValue.value
                                scaleY = scaleX
                            }
                            .clip(RoundedCornerShape(32.dp)),
                        content = {
                            playerContent.invoke {
                                // 在PlayerContent完成组合后注册BackHandler，确保顺序正确
                                val navEventState = rememberNavigationEventState(
                                    currentInfo = NavigationEventInfo.None
                                )

                                // 处理导航事件，监听返回手势或按键
                                NavigationEventHandler(
                                    state = navEventState,
                                    isBackEnabled = bottomSheetState.isVisible,        // 是否启用返回功能
                                    onBackCancelled = { scope.launch { bottomSheetState.show() } },
                                    onBackCompleted = { scope.launch { bottomSheetState.hide() } }
                                )

                                // 处理预见性返回事件的动画效果
                                LaunchedEffect(navEventState.transitionState) {
                                    val transitionState = navEventState.transitionState
                                    if (transitionState is NavigationEventTransitionState.InProgress) {
                                        val targetProgress = transitionState.latestEvent.progress
                                        val targetPosition = bottomSheetState.anchoredDraggableState
                                            .progressToTargetPosition(targetProgress)
                                        val offset = targetPosition - bottomSheetState.anchoredDraggableState.offset
                                        bottomSheetState.anchoredDraggableState.dispatchRawDelta(offset)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
private fun AnchoredDraggableState<ModalBottomSheetValue>.progressToTargetPosition(progress: Float): Float {
    val expanded = anchors.positionOf(ModalBottomSheetValue.Expanded)
    val hidden = anchors.positionOf(ModalBottomSheetValue.Hidden)
    return lerp(start = expanded, stop = hidden, fraction = progress)
}

private fun Float.normalize(min: Float, max: Float): Float {
    return (this - min) / (max - min)
}