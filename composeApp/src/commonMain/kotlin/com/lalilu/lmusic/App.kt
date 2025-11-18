package com.lalilu.lmusic

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import co.touchlab.kermit.Logger
import com.lalilu.LMusicTheme
import com.lalilu.common.ext.io
import com.lalilu.krouter.KRouter
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmusic.screen.ExceptionScreen
import com.lalilu.lplayer.LPlayer
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.LocalSharedTransitionScope
import com.lalilu.navigation.Screen
import com.lalilu.navigation.toNavEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
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

            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalBackStack provides backStack
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
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

                    with(LocalSharedTransitionScope.current) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(color = MaterialTheme.colorScheme.onBackground.copy(0.05f))
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp)
                                .renderInSharedTransitionScopeOverlay(
                                    zIndexInOverlay = 10f
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End)
                        ) {
                            Button(
                                modifier = Modifier,
                                onClick = {
                                    scope.launch(Dispatchers.io) {
                                        val list = LMedia.instance.get<LAudio>()
                                        LPlayer.instance.updatePlaylist(list)
                                        Logger.i("[LPlayer] set list: ${list.size}")
                                    }
                                }) {
                                Text("reset")
                            }
                        }
                    }
                }
            }
        }
    }
}