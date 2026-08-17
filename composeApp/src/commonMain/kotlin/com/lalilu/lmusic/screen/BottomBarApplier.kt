/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.lalilu.lmusic.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.lalilu.atLeastMedium
import com.lalilu.component.BottomSheetValue
import com.lalilu.component.ModalBottomSheetValue
import com.lalilu.component.rememberBottomSheetState
import com.lalilu.component.rememberModalBottomSheetState
import com.lalilu.krouter.KRouter
import com.lalilu.lmusic.component.PlayerBottomSheetContent
import com.lalilu.lmusic.component.PlayerBottomSheetScaffold
import com.lalilu.lmusic.component.ScaleBottomSheetLayout
import com.lalilu.lmusic.kRouterInjectMap
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.Screen
import com.lalilu.navigation.smartbar.NavigationSmartBar
import com.lalilu.preview.preview


@Composable
fun BottomBarApplier(
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier,
    tabsScreen: () -> List<Screen> = { emptyList() },
    content: @Composable (isBottomSheetVisible: () -> Boolean) -> Unit
) {
    val windowClass = currentWindowAdaptiveInfo().windowSizeClass
    val bottomSheetStateForPad = rememberBottomSheetState(BottomSheetValue.Collapsed)
    val bottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )

    val playerScreen = remember { AppRouter.route("/player").get() ?: ExceptionScreen.SCREEN_NOT_FOUND }
    val padPlayerScreen = remember { AppRouter.route("/player_pad").get() ?: ExceptionScreen.SCREEN_NOT_FOUND }

    val mainContent = remember(content) {
        movableContentOf<() -> Boolean> { content(it) }
    }
    val smartBarContent = remember {
        movableContentOf<Modifier, Boolean> { modifier, hideTabBar ->
            NavigationSmartBar(
                modifier = modifier,
                tabScreens = tabsScreen,
                hideTabBar = { hideTabBar }
            )
        }
    }
    val playerContent = remember {
        movableContentOf<@Composable () -> Unit> { postContent ->
            Box(modifier = Modifier.background(color = MaterialTheme.colorScheme.background)) {
                playerScreen.Content()
                postContent()
            }
        }
    }
    val padPlayerContent = remember {
        movableContentOf {
            PlayerBottomSheetContent(
                modifier = Modifier,
                bottomSheetState = bottomSheetStateForPad,
                playerScreen = { padPlayerScreen.Content() },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (windowClass.atLeastMedium()) {
            PlayerBottomSheetScaffold(
                modifier = modifier,
                bottomBarModifier = bottomBarModifier,
                bottomSheetState = bottomSheetStateForPad,
                smartBarContent = { smartBarContent.invoke(it, true) },
                playerContent = { padPlayerContent.invoke() },
                mainContent = { mainContent.invoke { !bottomSheetStateForPad.isExpanded } }
            )
        } else {
            ScaleBottomSheetLayout(
                modifier = modifier,
                bottomBarModifier = bottomBarModifier,
                bottomSheetState = bottomSheetState,
                playerContent = { playerContent.invoke(it) },
                mainContent = { mainContent.invoke { bottomSheetState.isVisible } },
                smartBarContent = { smartBarContent.invoke(it, false) }
            )
        }
    }
}


@Preview(device = Devices.TABLET)
@Composable
private fun PlayBottomBarPreview() = preview {
    KRouter.init(kRouterInjectMap()::getMap)
    BottomBarApplier {}
}