package com.lalilu.lmusic

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
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
@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLookaheadAnimationVisualDebugApi::class
)
@Composable
fun App() = ScreenModeHandler {
    ComposeFoundationFlags.isSkipItemPlacementAnimationFixEnabled = false

    // 构建导航栈
    val backStack = backStackHandler()
    val sidebarItems = remember {
        listOf(
            NavSidebarItem.NavSection(
                title = "Discover",
                screens = listOfNotNull(
                    AppRouter.route("/home").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/pages/albums").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/pages/artists").get() ?: ExceptionScreen.SCREEN_NOT_FOUND
                )
            ),
            NavSidebarItem.NavSection(
                title = "Library",
                screens = listOfNotNull(
                    AppRouter.route("/pages/history").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/log").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/media_source").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                    AppRouter.route("/settings").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
                )
            ),
            NavSidebarItem.Divider
        )
    }
    val tabsScreen = remember {
        listOfNotNull(
            AppRouter.route("/home").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
            AppRouter.route("/pages/playlist").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
            AppRouter.route("/settings").get() ?: ExceptionScreen.SCREEN_NOT_FOUND,
        )
    }

    LMusicTheme {
        LookaheadAnimationVisualDebugging(isEnabled = false) {
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
                                NavSideApplier(
                                    modifier = Modifier.fillMaxSize(),
                                    sidebarModifier = Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 10f),
                                    items = sidebarItems,
                                    isSelected = { it.key == backStack.lastOrNull()?.key },
                                    onSelectScreen = { it?.let { element -> backStack.add(element) } }
                                ) {
                                    AppNavHost(
                                        backStack = backStack,
                                        sharedTransitionScope = this@shareScope,
                                        animationSpec = animationSpec,
                                        isBackPressEnabled = isBottomSheetVisible,
                                    )
                                }
                            }

                            DialogWrapper.Content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 自定义 NavHost：
 * - 使用上游最底层的 NavDisplay(sceneState, navigationEventState, ...) 重载
 * - 自己注册 NavigationBackHandler，让 [isBackPressEnabled]（BottomSheet 状态）能控制 NavDisplay 的返回事件是否启用
 * - 当 BottomSheet 展开时，BottomSheet 的 BackHandler 优先消费返回事件；NavDisplay 在 BottomSheet 收起时才响应返回
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppNavHost(
    backStack: NavBackStack<Screen>,
    sharedTransitionScope: SharedTransitionScope,
    animationSpec: SpringSpec<IntOffset>,
    isBackPressEnabled: () -> Boolean,
) {
    val onBack: () -> Unit = remember(backStack) {
        { backStack.removeLastOrNull() }
    }

    val entryDecorators: List<NavEntryDecorator<Screen>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
        rememberDefaultBackgroundColorNavEntryDecorator(),
    ) as List<NavEntryDecorator<Screen>>

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = entryDecorators,
        entryProvider = { it.toNavEntry() },
    )

    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = listOf<SceneStrategy<Screen>>(SinglePaneSceneStrategy()),
        sharedTransitionScope = sharedTransitionScope,
        onBack = onBack,
    )

    val navigationEventState: NavigationEventState<SceneInfo<Screen>> =
        rememberNavigationEventState(
            currentInfo = SceneInfo(sceneState.currentScene),
            backInfo = sceneState.previousScenes.map { SceneInfo(it) },
        )

    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = sceneState.currentScene.previousEntries.isNotEmpty()
                && isBackPressEnabled(),
        onBackCompleted = {
            // 与上游 NavDisplay(entries, ..., onBack) 内部 onBackCompleted 行为完全对齐：
            // 若 enabled 在同一帧失效（gesture 已 dispatch），可能少 pop 几个，但避免 IndexOutOfBounds
            repeat(entries.size - sceneState.currentScene.previousEntries.size) {
                onBack()
            }
        },
    )

    NavDisplay(
        sceneState = sceneState,
        navigationEventState = navigationEventState,
        modifier = Modifier.fillMaxSize()
            .preferredFrameRate(FrameRateCategory.High),
        transitionSpec = {
            slideInVertically(animationSpec) { 100 } + fadeIn(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium
                )
            ) togetherWith
                    slideOutVertically(animationSpec) { 100 } + fadeOut(
                spring(
                    stiffness = Spring.StiffnessMedium
                )
            )
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
    )
}

@Composable
fun backStackHandler(): NavBackStack<Screen> {
    val backStack = retain {
        val homeScreen = AppRouter.route("/home").get() ?: ExceptionScreen.SCREEN_NOT_FOUND
        NavBackStack(homeScreen)
    }

    // 绑定AppRouter导航
    LaunchedEffect(Unit) {
        AppRouter.bind(backStack)
    }

    return backStack
}