package com.lalilu.lmusic

import androidx.compose.animation.*
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.navEntryDecorator
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.scene.rememberSceneSetupNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.lalilu.LMusicTheme
import com.lalilu.krouter.KRouter
import com.lalilu.lmusic.screen.ExceptionScreen
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.LocalSharedTransitionScope
import com.lalilu.navigation.Screen
import com.lalilu.navigation.toNavEntry
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val DEFAULT_TRANSITION_DURATION_MILLISECOND = 3000

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
                navEntryDecorator<NavKey> { entry ->
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
                navEntryDecorator<NavKey> { entry ->
                    Box(
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                        content = { entry.Content() }
                    )
                }
            }

            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalBackStack provides backStack
            ) {

                Column {
                    NavDisplay(
                        modifier = Modifier.fillMaxWidth()
                            .weight(1f)
                            .preferredFrameRate(FrameRateCategory.High),
                        backStack = backStack,
                        entryDecorators = listOf(
                            sharedEntryInSceneNavEntryDecorator,
                            rememberSceneSetupNavEntryDecorator(),
                            rememberSavedStateNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                            screenBackgroundDecorator
                        ) as List<NavEntryDecorator<Screen>>,
                        transitionSpec = {
                            ContentTransform(
                                fadeIn(animationSpec = tween(DEFAULT_TRANSITION_DURATION_MILLISECOND)),
                                fadeOut(animationSpec = tween(DEFAULT_TRANSITION_DURATION_MILLISECOND)),
                            )
                        },
                        popTransitionSpec = {
                            ContentTransform(
                                fadeIn(animationSpec = tween(DEFAULT_TRANSITION_DURATION_MILLISECOND)),
                                fadeOut(animationSpec = tween(DEFAULT_TRANSITION_DURATION_MILLISECOND)),
                            )
                        },
                        predictivePopTransitionSpec = {
                            ContentTransform(
                                fadeIn(animationSpec = tween(DEFAULT_TRANSITION_DURATION_MILLISECOND)),
                                fadeOut(animationSpec = tween(DEFAULT_TRANSITION_DURATION_MILLISECOND)),
                            )
                        },
                        entryProvider = { it.toNavEntry() }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(8.dp)
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