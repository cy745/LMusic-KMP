package com.lalilu.lmedia.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.lalilu.RemixIcon
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.rpc.RemoteServerPanel
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.LocalSharedTransitionScope
import com.lalilu.navigation.Screen
import com.lalilu.remixicon.Arrows
import com.lalilu.remixicon.arrows.arrowLeftLine
import org.koin.compose.koinInject


@Destination("/media_source")
object MediaSourceScreen : Screen {

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val platformSource = koinInject<PlatformMediaSource>()
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val column = when {
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 3
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 2
            else -> 1
        }

        LazyVerticalStaggeredGrid(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            columns = StaggeredGridCells.Fixed(column),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 16.dp
        ) {
            item {
                val backStack = LocalBackStack.current
                with(LocalSharedTransitionScope.current) {
                    Button(
                        modifier = Modifier
                            .wrapContentWidth()
                            .height(48.dp)
                            .sharedElementWithCallerManagedVisibility(
                                sharedContentState = rememberSharedContentState("test"),
                                visible = backStack.last() is MediaSourceScreen
                            ),
                        onClick = { if (backStack.size >= 2) backStack.removeLastOrNull() }
                    ) {
                        Icon(
                            imageVector = RemixIcon.Arrows.arrowLeftLine,
                            contentDescription = null
                        )
                    }
                }
            }
            item {
                RemoteServerPanel(modifier = Modifier.fillMaxWidth())
            }
            items(
                items = platformSource.sources,
                key = { it.name },
            ) { source ->
                source.Content(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}