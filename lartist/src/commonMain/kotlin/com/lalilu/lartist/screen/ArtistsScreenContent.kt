package com.lalilu.lartist.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.extensions.SharedMap
import com.lalilu.lartist.component.ArtistCard
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.sortable.SortResult
import com.lalilu.navigation.smartbar.NavigatorHeader

@Composable
internal fun ArtistsScreenContent(
    modifier: Modifier = Modifier,
    artists: SortResult<LArtist> = SortResult.empty(),
    onClickArtist: (LArtist, SharedMap) -> Unit
) {
    val statusBar = WindowInsets.statusBars
    val statusBarPadding = statusBar.asPaddingValues()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBarPadding.calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 16.dp
        ),
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
                artist = artist,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .padding(vertical = 0.5.dp),
                sharedMapPrefix = "list",
                onClick = { onClickArtist.invoke(artist, it) },
            )
        }
    }
}
