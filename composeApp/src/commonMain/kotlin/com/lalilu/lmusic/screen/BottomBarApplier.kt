/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.lalilu.lmusic.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lalilu.atLeastMedium
import com.lalilu.extensions.ClassicBackHandler
import com.lalilu.krouter.KRouter
import com.lalilu.krouter.generated.KRouterInjectMap
import com.lalilu.navigation.smartbar.NavigationSmartBar
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.screen.PlayerScreen
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.Screen
import com.lalilu.navigation.actualScreen
import com.lalilu.preview.preview
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


@Composable
fun BottomBarApplier(
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val windowClass = currentWindowAdaptiveInfo().windowSizeClass
    val navigatorBar = WindowInsets.navigationBars.asPaddingValues()
    val bottomSheetState = rememberBottomSheetState(BottomSheetValue.Collapsed)
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState)
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

    val mainContent = remember(content) {
        movableContentOf { content() }
    }
    val smartBarContent = remember {
        movableContentOf<Modifier> { modifier -> NavigationSmartBar(modifier = modifier) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (windowClass.atLeastMedium()) {
            BottomSheetScaffold(
                modifier = Modifier.fillMaxSize(),
                scaffoldState = bottomSheetScaffoldState,
                backgroundColor = Color.Transparent,
                sheetBackgroundColor = Color.Transparent,
                sheetPeekHeight = 72.dp + navigatorBar.calculateBottomPadding(),
                sheetContent = {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                            runCatching { KRouter.route<Screen>("/player_pad") }
                                .getOrNull()
                                ?.Content()
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
                },
                content = {
                    mainContent.invoke()
                }
            )
        } else {
            mainContent.invoke()

            val backStack = LocalBackStack.current
            val currentScreen = backStack.lastOrNull()
                ?.actualScreen()

            AnimatedVisibility(
                modifier = bottomBarModifier.align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                visible = currentScreen !is PlayerScreen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Row(
                    modifier = bottomBarModifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.background.copy(0.6f))
                        .height(72.dp + navigatorBar.calculateBottomPadding())
                ) {
                    smartBarContent.invoke(Modifier.fillMaxSize())
                }
            }
        }
    }

    if (!LocalInspectionMode.current) {
        // 监听用户的返回操作
        ClassicBackHandler(enabled = bottomSheetState.isExpanded) {
            scope.launch { bottomSheetState.collapse() }
        }
    }
}


@Preview(device = Devices.TABLET)
@Composable
private fun PlayBottomBarPreview() = preview {
    KRouter.init(KRouterInjectMap::getMap)
    BottomBarApplier {}
}