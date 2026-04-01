package com.lalilu.lmusic.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.lalilu.component.ModalBottomSheetLayout
import com.lalilu.component.ModalBottomSheetState
import com.lalilu.extensions.ClassicBackHandler
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
                                ClassicBackHandler(enabled = bottomSheetState.isVisible) {
                                    scope.launch { bottomSheetState.hide() }
                                }
                            }
                        }
                    )
                }
            }
        )
    }
}

private fun Float.normalize(min: Float, max: Float): Float {
    return (this - min) / (max - min)
}