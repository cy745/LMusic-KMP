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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.OverrideNavDisplay
import com.lalilu.LMusicTheme
import com.lalilu.krouter.KRouter
import com.lalilu.lmusic.screen.ExceptionScreen
import com.lalilu.navigation.*

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    val backStack = remember {
        mutableStateListOf<Screen>(
            KRouter.route("/player")
                ?: KRouter.route("/home")
                ?: ExceptionScreen.SCREEN_NOT_FOUND
        )
    }

    LMusicTheme {
        SharedTransitionLayout {
            val sharedEntryInSceneNavEntryDecorator = remember {
                NavEntryDecorator<NavKey> { entry ->
                    with(LocalSharedTransitionScope.current) {
                        Box(
                            Modifier.sharedElement(
                                rememberSharedContentState(entry.contentKey),
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                            ),
                        ) {
                            entry.Content()
                        }
                    }
                }
            }
            val screenBackgroundDecorator = remember {
                NavEntryDecorator<NavKey> { entry ->
                    Box(
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                        content = { entry.Content() }
                    )
                }
            }

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
                    OverrideNavDisplay(
                        modifier = Modifier.fillMaxSize()
                            .preferredFrameRate(FrameRateCategory.High),
                        backStack = backStack,
                        transitionState = { scene ->
                            remember { SeekableTransitionState(scene) }
                                .also { transitionState.value = it }
                        },
                        entryDecorators = listOf(
                            sharedEntryInSceneNavEntryDecorator,
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                            screenBackgroundDecorator
                        ) as List<NavEntryDecorator<Screen>>,
                        transitionSpec = {
                            slideInVertically(animationSpec) { 100 } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                    slideOutVertically(animationSpec) { 100 } + fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        },
                        popTransitionSpec = {
                            slideInVertically(animationSpec) { -100 } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                    slideOutVertically(animationSpec) { -100 } + fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        },
                        predictivePopTransitionSpec = {
                            slideInVertically(animationSpec) { -100 } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                    slideOutVertically(animationSpec) { -100 } + fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        },
                        entryProvider = { it.toNavEntry() }
                    )
                }
            }
        }
    }
}