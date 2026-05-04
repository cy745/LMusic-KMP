package com.lalilu.lmusic

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalOnBackPressEnableState
import androidx.navigation3.ui.NavDisplay
import com.lalilu.LMusicTheme
import com.lalilu.ScreenModeHandler
import com.lalilu.component.rememberCupertinoOverscrollEffectFactory
import com.lalilu.extensions.DialogWrapper
import com.lalilu.extensions.ProvideLocalToaster
import com.lalilu.lmusic.screen.BottomBarApplier
import com.lalilu.lmusic.screen.ExceptionScreen
import com.lalilu.lmusic.screen.NavSideApplier
import com.lalilu.lmusic.screen.NavSidebarItem
import com.lalilu.lmusic.util.handleMouseBackPress
import com.lalilu.navigation.*

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun App() = ScreenModeHandler {
    // 构建导航栈
    val backStack = backStackHandler()
    val sidebarItems = remember {
        listOf(
            NavSidebarItem.NavSection(
                title = "Discover",
                screens = listOfNotNull(
                    AppRouter.route("/home").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/albums").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/artists").get() ?: ExceptionScreen.SCREEN_NOT_FOUND
                )
            ),
            NavSidebarItem.NavSection(
                title = "Library",
                screens = listOfNotNull(
                    AppRouter.route("/history").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/log").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/media_source").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                )
            ),
            NavSidebarItem.Divider
        )
    }
    val tabsScreen = remember {
        listOfNotNull(
            AppRouter.route("/home").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
            AppRouter.route("/pages/playlist").get(),
            AppRouter.route("/log").get()
        )
    }

    LMusicTheme {
        SharedTransitionLayout shareScope@{
            val animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntOffset.VisibilityThreshold
            )

            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalBackStack provides backStack,
                LocalOverscrollFactory provides rememberCupertinoOverscrollEffectFactory()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .handleMouseBackPress()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    ProvideLocalToaster {
                        BottomBarApplier(
                            modifier = Modifier.fillMaxSize(),
                            bottomBarModifier = Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 20f),
                            tabsScreen = { tabsScreen }
                        ) { isBottomSheetVisible ->
                            CompositionLocalProvider(
                                LocalOnBackPressEnableState provides isBottomSheetVisible
                            ) {
                                NavSideApplier(
                                    modifier = Modifier.fillMaxSize(),
                                    sidebarModifier = Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 10f),
                                    items = sidebarItems,
                                    isSelected = { it.key == backStack.lastOrNull()?.key },
                                    onSelectScreen = { it?.let { element -> backStack.add(element) } }
                                ) {
                                    NavDisplay(
                                        modifier = Modifier.fillMaxSize()
                                            .preferredFrameRate(FrameRateCategory.High),
                                        backStack = backStack,
                                        sharedTransitionScope = this@shareScope,
                                        entryDecorators = listOf(
                                            rememberSaveableStateHolderNavEntryDecorator(),
                                            rememberViewModelStoreNavEntryDecorator(),
                                            rememberDefaultBackgroundColorNavEntryDecorator()
                                        ) as List<NavEntryDecorator<Screen>>,
                                        transitionSpec = {
                                            slideInVertically(animationSpec) { 100 } + fadeIn(
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            ) togetherWith
                                                    slideOutVertically(animationSpec) { 100 } + fadeOut(spring(stiffness = Spring.StiffnessMedium))
                                        },
                                        popTransitionSpec = {
                                            slideInVertically(animationSpec) { -100 } + fadeIn(
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            ) togetherWith
                                                    slideOutVertically(animationSpec) { -100 } + fadeOut(
                                                spring(
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        },
                                        predictivePopTransitionSpec = {
                                            slideInVertically(animationSpec) { -100 } + fadeIn(
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            ) togetherWith
                                                    slideOutVertically(animationSpec) { -100 } + fadeOut(
                                                spring(
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        },
                                        entryProvider = { it.toNavEntry() }
                                    )
                                }
                            }
                        }

                        DialogWrapper.Content()
                    }
                }
            }
        }
    }
}

@Composable
fun backStackHandler(): NavBackStack<Screen> {
    val homeScreen = remember { AppRouter.route("/home").get() ?: ExceptionScreen.SCREEN_NOT_FOUND }
    val backStack = remember { NavBackStack(homeScreen) }

    // 绑定AppRouter导航
    LaunchedEffect(Unit) {
        AppRouter.bind(backStack)
    }

    return backStack
}