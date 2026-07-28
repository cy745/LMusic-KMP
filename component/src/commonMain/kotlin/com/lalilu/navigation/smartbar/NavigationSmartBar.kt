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

package com.lalilu.navigation.smartbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.lalilu.navigation.*
import kotlinx.coroutines.flow.filterNotNull


/**
 * 智能导航栏密封类，用于区分不同类型的导航栏
 */
private sealed interface NavigationBarType {
    /**
     * Tab 类型的导航栏（主页面）
     */
    data object TabBar : NavigationBarType

    /**
     * 通用导航栏（详情页面，带返回按钮）
     */
    open class CommonBar(val actions: List<ScreenAction>) : NavigationBarType

    /**
     * 自定义导航栏（由 Screen 提供）
     */
    data class NormalBar(val barComponent: ScreenBarComponent) : NavigationBarType

    /**
     * 空导航栏
     */
    data object EmptyBar : CommonBar(emptyList())
}

/**
 * 智能导航栏组件
 *
 * 根据当前屏幕类型自动选择显示:
 * - [NavigationBarType.TabBar]: 主 Tab 页面
 * - [NavigationBarType.CommonBar]: 详情页面（带返回按钮和操作按钮）
 * - [NavigationBarType.NormalBar]: 自定义导航栏（由 Screen 提供）
 */
@Composable
fun NavigationSmartBar(
    modifier: Modifier = Modifier,
    tabScreens: () -> List<Screen> = { emptyList() },
) {
    val backStack = LocalBackStack.current
    val navigationBar = remember { mutableStateOf<NavigationBarType>(NavigationBarType.EmptyBar) }

    LaunchedEffect(Unit) {
        snapshotFlow {
            val currentScreen = backStack.lastOrNull()?.actualScreen()
            SmartbarStackHolder.stackMap[currentScreen?.key]
                ?.let { Triple(it.first, it.second, currentScreen) }
        }.filterNotNull()
            .collect { (barComponent, actions, currentScreen) ->
                navigationBar.value = when {
                    barComponent != null -> NavigationBarType.NormalBar(barComponent)
                    currentScreen is ScreenInfoFactory && currentScreen.isTabScreen() -> NavigationBarType.TabBar
                    actions != null -> NavigationBarType.CommonBar(actions)
                    else -> NavigationBarType.EmptyBar
                }
            }
    }

    AnimatedContent(
        modifier = modifier
            .pointerInput(Unit) { } // 避免点击穿透
            .fillMaxHeight(),
        transitionSpec = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Up,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) togetherWith slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Down,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        },
        contentAlignment = Alignment.BottomCenter,
        targetState = navigationBar.value,
        label = "NavigationBar"
    ) { item ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (item) {
                is NavigationBarType.NormalBar -> {
                    item.barComponent.content()
                }

                is NavigationBarType.TabBar -> {
                    val currentScreen = backStack.lastOrNull()?.actualScreen()

                    NavigateTabBar(
                        modifier = Modifier.fillMaxHeight(),
                        currentScreen = { currentScreen },
                        tabScreens = tabScreens,
                        onSelectTab = { screen -> AppRouter.intent(NavIntent.Push(screen)) }
                    )
                }

                is NavigationBarType.CommonBar -> {
                    val previousScreen = backStack
                        .takeIf { it.size > 1 }
                        ?.let { it[it.size - 2] }
                        ?.actualScreen()

                    val previousTitle = if (previousScreen == null) {
                        null
                    } else {
                        (previousScreen as? ScreenInfoFactory)
                            ?.provideScreenInfo()
                            ?.title?.invoke()
                            ?: "Back"
                    }

                    NavigateCommonBar(
                        modifier = Modifier.fillMaxHeight(),
                        previousScreenTitle = previousTitle,
                        screenActions = { item.actions },
                        onBackPress = { AppRouter.intent(NavIntent.Pop) }
                    )
                }
            }
        }
    }
}
