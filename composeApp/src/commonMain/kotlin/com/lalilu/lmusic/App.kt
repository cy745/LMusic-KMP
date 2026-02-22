package com.lalilu.lmusic

import androidx.compose.animation.*
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.lalilu.LMusicTheme
import com.lalilu.ScreenMode
import com.lalilu.ScreenMode.*
import com.lalilu.ScreenModeHandler
import com.lalilu.currentScreenMode
import com.lalilu.extensions.ProvideLocalToaster
import com.lalilu.lmusic.screen.ExceptionScreen
import com.lalilu.lmusic.screen.NavSideApplier
import com.lalilu.lmusic.screen.NavSidebarItem
import com.lalilu.lmusic.screen.BottomBarApplier
import com.lalilu.navigation.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
                    AppRouter.route("/player").get() ?: ExceptionScreen.SCREEN_NOT_FOUND
                )
            ),
            NavSidebarItem.NavSection(
                title = "Library",
                screens = listOfNotNull(
                    AppRouter.route("/log").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/media_source").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                )
            ),
            NavSidebarItem.Divider
        )
    }

    LMusicTheme {
        SharedTransitionLayout shareScope@{
            val animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntOffset.VisibilityThreshold
            )

            val transitionState = remember {
                mutableStateOf<SeekableTransitionState<Scene<Screen>>?>(null)
            }

            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalBackStack provides backStack,
                LocalNavSeekableTransitionState provides transitionState
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    ProvideLocalToaster {
                        BottomBarApplier(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            NavSideApplier(
                                modifier = Modifier.fillMaxSize(),
                                items = sidebarItems,
                                isSelected = { it.key == backStack.lastOrNull()?.key },
                                onSelectScreen = { it?.let { element -> backStack.add(element) } }
                            ) {
                                NavDisplay(
                                    modifier = Modifier.fillMaxSize()
                                        .preferredFrameRate(FrameRateCategory.High),
                                    backStack = backStack,
                                    transitionState = { scene ->
                                        remember { SeekableTransitionState(scene) }
                                            .also { transitionState.value = it }
                                    },
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
                                                slideOutVertically(animationSpec) { -100 } + fadeOut(spring(stiffness = Spring.StiffnessMedium))
                                    },
                                    predictivePopTransitionSpec = {
                                        slideInVertically(animationSpec) { -100 } + fadeIn(
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        ) togetherWith
                                                slideOutVertically(animationSpec) { -100 } + fadeOut(spring(stiffness = Spring.StiffnessMedium))
                                    },
                                    entryProvider = { it.toNavEntry() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun backStackHandler(): NavBackStack<Screen> {
    val screenMode: ScreenMode = currentScreenMode()
    val playerScreen = remember { AppRouter.route("/player").get() ?: ExceptionScreen.SCREEN_NOT_FOUND }
    val homeScreen = remember { AppRouter.route("/home").get() ?: ExceptionScreen.SCREEN_NOT_FOUND }
    val mutex = remember { Mutex() }
    val backStack = remember {
        NavBackStack(
            when (screenMode) {
                Phone -> playerScreen
                Tablet -> homeScreen
                Unknown -> ExceptionScreen.SCREEN_NOT_FOUND
            }
        )
    }

    // 屏幕模式切换控制导航栈
    LaunchedEffect(screenMode) {
        mutex.withLock {
            ensureActive()

            fun firstScreen() = backStack.firstOrNull()

            when (screenMode) {
                Phone -> {
                    if (firstScreen() != playerScreen) {
                        backStack.add(0, playerScreen)
                    }
                }

                Tablet -> {
                    if (firstScreen() == playerScreen) {
                        backStack.removeAt(0)
                    }
                    if (firstScreen() != homeScreen) {
                        backStack.add(0, homeScreen)
                    }
                }

                else -> {
                }
            }
        }
    }

    // 绑定AppRouter导航
    LaunchedEffect(Unit) {
        AppRouter.bind(backStack)
    }

    return backStack
}