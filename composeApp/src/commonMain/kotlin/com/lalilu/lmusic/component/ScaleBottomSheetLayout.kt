package com.lalilu.lmusic.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.max
import androidx.compose.ui.util.lerp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.lalilu.component.AnchoredDraggableState
import com.lalilu.component.ModalBottomSheetLayout
import com.lalilu.component.ModalBottomSheetState
import com.lalilu.component.ModalBottomSheetValue
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.navigation.LocalModalBottomSheetState
import com.lalilu.navigation.SheetExpandInterceptor
import kotlinx.coroutines.launch


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
    val ime = WindowInsets.ime.asPaddingValues()

    // 注册"路由跳转后展开底栏"回调：AppRouter 末尾的 SheetExpandInterceptor
    // 在真实跳转时调用。拦截器无法访问 CompositionLocal，只能通过该全局
    // 注册表间接驱动；组合销毁时清理，避免泄漏到其他布局（如平板布局
    // 不注册则跳转不触发展开）。
    DisposableEffect(Unit) {
        SheetExpandInterceptor.expandModalSheet = { scope.launch { bottomSheetState.show() } }
        onDispose { SheetExpandInterceptor.expandModalSheet = null }
    }

    CompositionLocalProvider(LocalModalBottomSheetState provides bottomSheetState) {
        ModalBottomSheetLayout(
            modifier = modifier.fillMaxSize(),
            sheetState = bottomSheetState,
            sheetShape = RectangleShape,
            sheetContentColor = MaterialTheme.colorScheme.background,
            scrimColor = Color.Black.copy(0.3f),
            sheetContent = {
                Box(modifier = Modifier) {
                    PassThroughHelper.Passthrough(
                        "SmartBarHeight" to {
                            72.dp + ime.calculateBottomPadding()
                                .coerceAtLeast(navigatorBar.calculateBottomPadding())
                        }
                    ) {
                        mainContent.invoke()
                    }

                    val navigatorBarHeight = navigatorBar.calculateBottomPadding()
                    val imeHeight = ime.calculateBottomPadding()

                    smartBarContent.invoke(
                        bottomBarModifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(color = MaterialTheme.colorScheme.background.copy(0.9f))
                            .padding(bottom = max(navigatorBarHeight, imeHeight))
                            .height(72.dp)
                    )
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

private fun AnchoredDraggableState<ModalBottomSheetValue>.progressToTargetPosition(progress: Float): Float {
    val expanded = anchors.positionOf(ModalBottomSheetValue.Expanded)
    val hidden = anchors.positionOf(ModalBottomSheetValue.Hidden)
    return lerp(start = expanded, stop = hidden, fraction = progress)
}

private fun Float.normalize(min: Float, max: Float): Float {
    return (this - min) / (max - min)
}