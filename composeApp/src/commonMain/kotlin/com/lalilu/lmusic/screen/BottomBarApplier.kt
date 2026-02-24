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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lalilu.adaptiveValue
import com.lalilu.animated
import com.lalilu.extensions.ClassicBackHandler
import com.lalilu.krouter.KRouter
import com.lalilu.krouter.generated.KRouterInjectMap
import com.lalilu.lplayer.LPlayer
import com.lalilu.navigation.Screen
import com.lalilu.preview.preview
import kotlinx.coroutines.launch


@Composable
fun BottomBarApplier(
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val navigatorBar = WindowInsets.navigationBars.asPaddingValues()
    val bottomSheetState = rememberBottomSheetState(BottomSheetValue.Collapsed)
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState)

    val peekHeight = adaptiveValue(
        compact = { 0.dp },
        medium = { 72.dp + navigatorBar.calculateBottomPadding() },
    ).animated()

    BottomSheetScaffold(
        modifier = modifier.fillMaxSize(),
        scaffoldState = bottomSheetScaffoldState,
        backgroundColor = Color.Transparent,
        sheetPeekHeight = peekHeight.value,
        sheetContent = {
            if (peekHeight.value > 0.dp) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    runCatching { KRouter.route<Screen>("/player_pad") }
                        .getOrNull()
                        ?.Content()

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
                            .height(72.dp + navigatorBar.calculateBottomPadding())
                            .background(color = MaterialTheme.colorScheme.background)
                            .padding(start = 80.dp)
                            .padding(bottom = navigatorBar.calculateBottomPadding()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentPlaying = LPlayer.instance.currentItem.collectAsState(null)

                        PlayingInfoCard(
                            currentPlaying = { currentPlaying.value },
                            onClick = { scope.launch { bottomSheetState.expand() } }
                        )
                    }
                }
            }
        },
        content = {
            content()
        }
    )

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