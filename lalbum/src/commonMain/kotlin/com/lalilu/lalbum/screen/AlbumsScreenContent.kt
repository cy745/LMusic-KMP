package com.lalilu.lalbum.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.extensions.SharedMap
import com.lalilu.lalbum.component.AlbumCard
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.navigation.smartbar.NavigatorHeader

@Composable
internal fun AlbumsScreenContent(
    modifier: Modifier = Modifier,
    albums: SortResult<LAlbum> = SortResult.empty(),
    showText: () -> Boolean = { true },
    onClickAlbum: (LAlbum, SharedMap) -> Unit
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columns = when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 4
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 3
        else -> 2
    }

    val statusBar = WindowInsets.statusBars
    val statusBarPadding = statusBar.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 10.dp,
            end = 10.dp,
            top = statusBarPadding.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        item {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = "Albums",
                subTitle = "${albums.itemList.size} albums"
            )
        }

        items(
            items = albums.itemList,
            key = { it.id }
        ) { album ->
            AlbumCard(
                album = { album },
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                showTitle = { showText() },
                onClick = { onClickAlbum.invoke(album, it) },
            )
        }
    }
}
