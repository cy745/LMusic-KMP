package com.lalilu.lmedia.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.component.LocalWindowSizeClass
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.rpc.RemoteServerPanel
import io.github.hristogochev.vortex.screen.Screen
import org.koin.compose.koinInject


@Destination("/media_source")
object MediaSourceScreen : Screen {

    @Composable
    override fun Content() {
        val platformSource = koinInject<PlatformMediaSource>()
        val windowSizeClass = LocalWindowSizeClass.current
        val column = when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Medium -> 2
            WindowWidthSizeClass.Expanded -> 3
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