package com.lalilu.lmusic

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.lalilu.LMusicTheme
import com.lalilu.krouter.KRouter
import com.lalilu.lmusic.screen.ExceptionScreen
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.LocalSharedTransitionScope
import com.lalilu.navigation.Screen
import com.lalilu.navigation.toNavEntry

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    val backStack = remember {
        mutableStateListOf(
            KRouter.route<Screen>("/home")
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

            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalBackStack provides backStack
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavDisplay(
                        modifier = Modifier.fillMaxSize()
                            .preferredFrameRate(FrameRateCategory.High),
                        backStack = backStack,
                        entryDecorators = listOf(
                            sharedEntryInSceneNavEntryDecorator,
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                            screenBackgroundDecorator
                        ) as List<NavEntryDecorator<Screen>>,
                        transitionSpec = {
                            slideInVertically(animationSpec) { 100 } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                    slideOutVertically(animationSpec) { 100 } + fadeOut(tween(50))
                        },
                        popTransitionSpec = {
                            slideInVertically(animationSpec) { -100 } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                    slideOutVertically(animationSpec) { -100 } + fadeOut(tween(50))
                        },
                        predictivePopTransitionSpec = {
                            slideInVertically(animationSpec) { -100 } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                                    slideOutVertically(animationSpec) { -100 } + fadeOut(tween(50))
                        },
                        entryProvider = { it.toNavEntry() }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                    ) {
                        Button(onClick = {
                            if (backStack.size >= 2) {
                                backStack.removeLastOrNull()
                            }
                        }) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}