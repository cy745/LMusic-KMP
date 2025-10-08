package com.lalilu.lmusic

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
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
import com.lalilu.navigation.*
import org.jetbrains.compose.ui.tooling.preview.Preview


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
                val twoPaneStrategy = remember { TwoPaneSceneStrategy<Screen>() }

                Column {
                    NavDisplay(
                        modifier = Modifier.fillMaxWidth()
                            .weight(1f)
                            .preferredFrameRate(FrameRateCategory.High),
                        backStack = backStack,
                        sceneStrategy = twoPaneStrategy,
                        entryDecorators = listOf(
                            sharedEntryInSceneNavEntryDecorator,
                            rememberSceneSetupNavEntryDecorator(),
                            rememberSavedStateNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                            screenBackgroundDecorator
                        ) as List<NavEntryDecorator<Screen>>,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = { it.toNavEntry() }
                    )
                }
            }
        }
    }
}