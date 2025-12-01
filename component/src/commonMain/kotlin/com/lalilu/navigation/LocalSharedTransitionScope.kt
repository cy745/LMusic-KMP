package com.lalilu.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope =
    staticCompositionLocalOf<SharedTransitionScope> { error("No scope provided") }

@Composable
fun rememberSharedEntryDecorator(): NavEntryDecorator<NavKey> {
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
    return sharedEntryInSceneNavEntryDecorator
}