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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.lalilu.atLeastMedium
import com.lalilu.component.BottomSheetValue
import com.lalilu.component.ModalBottomSheetValue
import com.lalilu.component.rememberBottomSheetState
import com.lalilu.component.rememberModalBottomSheetState
import com.lalilu.extensions.koinInjectOrNull
import com.lalilu.krouter.KRouter
import com.lalilu.lmusic.component.PlayerBottomSheetContent
import com.lalilu.lmusic.component.PlayerBottomSheetScaffold
import com.lalilu.lmusic.component.ScaleBottomSheetLayout
import com.lalilu.lmusic.deeplink.PlayerBottomSheetController
import com.lalilu.lmusic.kRouterInjectMap
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.Screen
import com.lalilu.navigation.smartbar.NavigationSmartBar
import com.lalilu.preview.preview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


@Composable
fun BottomBarApplier(
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier,
    tabsScreen: () -> List<Screen> = { emptyList() },
    content: @Composable (isBottomSheetVisible: () -> Boolean) -> Unit
) {
    val windowClass = currentWindowAdaptiveInfo().windowSizeClass
    val usePadLayout = windowClass.atLeastMedium()
    val bottomSheetStateForPad = rememberBottomSheetState(BottomSheetValue.Collapsed)
    val bottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val scope = rememberCoroutineScope()
    val sheetAnimationController = remember(scope) {
        PlayerBottomSheetAnimationController(scope)
    }
    val playerBottomSheetController = koinInjectOrNull<PlayerBottomSheetController>()

    DisposableEffect(
        playerBottomSheetController,
        usePadLayout,
        bottomSheetStateForPad,
        bottomSheetState,
    ) {
        val registration = playerBottomSheetController?.register(
            isExpanded = {
                sheetAnimationController.isExpanded {
                    if (usePadLayout) {
                        bottomSheetStateForPad.targetValue == BottomSheetValue.Expanded
                    } else {
                        // 手机布局的 ModalBottomSheet 承载主页面：主页面隐藏时播放器才是展开状态。
                        bottomSheetState.targetValue == ModalBottomSheetValue.Hidden
                    }
                }
            },
            expand = {
                sheetAnimationController.animateTo(expanded = true) {
                    if (usePadLayout) {
                        bottomSheetStateForPad.awaitAnchor(BottomSheetValue.Expanded)
                        bottomSheetStateForPad.expand()
                    } else {
                        bottomSheetState.awaitAnchor(ModalBottomSheetValue.Hidden)
                        bottomSheetState.hide()
                    }
                }
            },
            collapse = {
                sheetAnimationController.animateTo(expanded = false) {
                    if (usePadLayout) {
                        bottomSheetStateForPad.awaitAnchor(BottomSheetValue.Collapsed)
                        bottomSheetStateForPad.collapse()
                    } else {
                        bottomSheetState.awaitAnchor(ModalBottomSheetValue.Expanded)
                        bottomSheetState.show()
                    }
                }
            },
        )

        onDispose {
            registration?.let { playerBottomSheetController.unregister(it) }
            sheetAnimationController.cancel()
        }
    }

    val playerScreen = remember { AppRouter.route("/player").get() ?: ExceptionScreen.SCREEN_NOT_FOUND }
    val padPlayerScreen = remember { AppRouter.route("/player_pad").get() ?: ExceptionScreen.SCREEN_NOT_FOUND }

    val mainContent = remember(content) {
        movableContentOf<() -> Boolean> { content(it) }
    }
    val smartBarContent = remember {
        movableContentOf<Modifier, Modifier, Boolean> { modifier, barModifier, hideTabBar ->
            NavigationSmartBar(
                modifier = modifier,
                barModifier = barModifier,
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
        if (usePadLayout) {
            PlayerBottomSheetScaffold(
                modifier = modifier,
                bottomBarModifier = bottomBarModifier,
                bottomSheetState = bottomSheetStateForPad,
                smartBarContent = { modifier, barModifier ->
                    smartBarContent.invoke(modifier, barModifier, true)
                },
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
                smartBarContent = { modifier ->
                    smartBarContent.invoke(modifier, Modifier, false)
                }
            )
        }
    }
}

private suspend fun com.lalilu.component.BottomSheetState.awaitAnchor(value: BottomSheetValue) {
    snapshotFlow { anchoredDraggableState.anchors.hasAnchorFor(value) }.first { it }
}

private suspend fun com.lalilu.component.ModalBottomSheetState.awaitAnchor(value: ModalBottomSheetValue) {
    snapshotFlow { anchoredDraggableState.anchors.hasAnchorFor(value) }.first { it }
}

/**
 * 串行化外部 BottomSheet 命令：新命令会取消尚未结束的旧动画，同时在动画期间保存目标状态，
 * 让连续触发 toggle 时依据上一次命令的目标切换，而不是读取仍在过渡中的瞬时 State。
 */
private class PlayerBottomSheetAnimationController(
    private val scope: CoroutineScope,
) {
    private var animationJob: Job? = null
    private var generation = 0L
    private var requestedExpanded: Boolean? = null

    fun isExpanded(fallback: () -> Boolean): Boolean = requestedExpanded ?: fallback()

    fun animateTo(expanded: Boolean, block: suspend () -> Unit) {
        val currentGeneration = ++generation
        requestedExpanded = expanded
        animationJob?.cancel()
        animationJob = scope.launch {
            try {
                block()
            } finally {
                if (generation == currentGeneration) {
                    requestedExpanded = null
                    animationJob = null
                }
            }
        }
    }

    fun cancel() {
        generation++
        requestedExpanded = null
        animationJob?.cancel()
        animationJob = null
    }
}


@Preview(device = Devices.TABLET)
@Composable
private fun PlayBottomBarPreview() = preview {
    KRouter.init(kRouterInjectMap()::getMap)
    BottomBarApplier {}
}
