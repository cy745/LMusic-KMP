package com.lalilu.lmusic

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.navEntryDecorator
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.lalilu.RemixIcon
import com.lalilu.krouter.KRouter
import com.lalilu.lmusic.screen.ExceptionScreen
import com.lalilu.navigation.*
import com.lalilu.remixicon.Arrows
import com.lalilu.remixicon.arrows.arrowLeftLine
import org.jetbrains.compose.ui.tooling.preview.Preview


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

    MaterialTheme {
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
                            .weight(1f),
                        backStack = backStack,
                        sceneStrategy = twoPaneStrategy,
                        entryDecorators = listOf(
                            sharedEntryInSceneNavEntryDecorator,
                            rememberSceneSetupNavEntryDecorator(),
                            rememberSavedStateNavEntryDecorator(),
                            screenBackgroundDecorator
                        ),
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider = { it.toNavEntry() }
                    )
                    Card {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { if (backStack.size >= 2) backStack.removeLastOrNull() }) {
                                Icon(
                                    imageVector = RemixIcon.Arrows.arrowLeftLine,
                                    contentDescription = null
                                )
                            }
                            Button(onClick = {
                                KRouter.route<Screen>("/log")
                                    ?.let { backStack.add(it) }
                            }) {
                                Icon(
                                    imageVector = RemixIcon.Arrows.arrowLeftLine,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}