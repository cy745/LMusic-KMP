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
    data class TabBar(val currentScreen: Screen) : NavigationBarType

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
                    currentScreen is ScreenInfoFactory && currentScreen.isTabScreen() ->
                        NavigationBarType.TabBar(currentScreen)

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
                    NavigateTabBar(
                        modifier = Modifier.fillMaxHeight(),
                        currentScreen = { item.currentScreen },
                        tabScreens = tabScreens,
                        onSelectTab = { screen -> backStack.add(screen) }
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
                        onBackPress = {
                            if (backStack.size > 1) {
                                backStack.removeAt(backStack.size - 1)
                            }
                        }
                    )
                }
            }
        }
    }
}
