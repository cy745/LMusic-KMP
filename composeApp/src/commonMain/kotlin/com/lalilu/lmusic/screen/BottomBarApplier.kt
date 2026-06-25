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
import com.lalilu.lmusic.kRouterInjectMapV2
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
        movableContentOf<Modifier> {
            NavigationSmartBar(
                modifier = it,
                tabScreens = tabsScreen
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
    val padPlayerContent = remember(smartBarContent) {
        movableContentOf {
            PlayerBottomSheetContent(
                modifier = Modifier,
                bottomBarModifier = bottomBarModifier,
                bottomSheetState = bottomSheetStateForPad,
                playerScreen = { padPlayerScreen.Content() },
                smartBarContent = { smartBarContent.invoke(it) }
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (windowClass.atLeastMedium()) {
            PlayerBottomSheetScaffold(
                modifier = modifier,
                bottomSheetState = bottomSheetStateForPad,
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
                smartBarContent = { smartBarContent.invoke(it) }
            )
        }
    }
}


@Preview(device = Devices.TABLET)
@Composable
private fun PlayBottomBarPreview() = preview {
    KRouter.init(kRouterInjectMapV2()::getMap)
    BottomBarApplier {}
}