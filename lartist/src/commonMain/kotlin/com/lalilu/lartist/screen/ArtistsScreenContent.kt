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
    searchKeyword: String = "",
    onClearSearch: () -> Unit = {},
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
                title = "歌手",
                subTitle = if (searchKeyword.isBlank()) "共 ${artists.itemList.size} 位歌手" else "搜索：$searchKeyword"
            )
        }

        if (artists.itemList.isEmpty()) {
            item { com.lalilu.component.LibraryEmptyState(
                    title = if (searchKeyword.isBlank()) "还没有歌手" else "没有找到相关歌手",
                    description = if (searchKeyword.isBlank()) "添加媒体源后，会根据歌曲信息整理歌手" else "试试其他歌手名称或清除搜索",
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 32.dp),
                    actionLabel = if (searchKeyword.isBlank()) "添加媒体源" else "清除搜索",
                    onAction = { if (searchKeyword.isBlank()) com.lalilu.navigation.AppRouter.route("/media_source").jump() else onClearSearch() },
                ) }
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
