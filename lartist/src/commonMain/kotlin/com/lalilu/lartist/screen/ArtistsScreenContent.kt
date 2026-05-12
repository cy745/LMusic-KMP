package com.lalilu.lartist.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.extensions.SharedMap
import com.lalilu.lartist.component.ArtistCard
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.navigation.smartbar.NavigatorHeader

@Composable
internal fun ArtistsScreenContent(
    modifier: Modifier = Modifier,
    artists: SortResult<LArtist> = SortResult.empty(),
    showText: () -> Boolean = { true },
    onClickArtist: (LArtist, SharedMap) -> Unit
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columns = if (windowSizeClass.windowWidthSizeClass.toString().contains("Expanded")) 3 else 2

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
                title = "Artists",
                subTitle = "${artists.itemList.size} artists"
            )
        }

        items(
            items = artists.itemList,
            key = { it.id }
        ) { artist ->
            ArtistCard(
                artist = { artist },
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                showTitle = { showText() },
                onClick = { onClickArtist.invoke(artist, it) },
            )
        }
    }
}
