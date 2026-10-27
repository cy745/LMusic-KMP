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
    searchKeyword: String = "",
    onClearSearch: () -> Unit = {},
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
        item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = "专辑",
                subTitle = if (searchKeyword.isBlank()) "共 ${albums.itemList.size} 张专辑" else "搜索：$searchKeyword"
            )
        }

        if (albums.itemList.isEmpty()) {
            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                com.lalilu.component.LibraryEmptyState(
                    title = if (searchKeyword.isBlank()) "还没有专辑" else "没有找到相关专辑",
                    description = if (searchKeyword.isBlank()) "添加媒体源后，会根据歌曲信息整理专辑" else "试试其他专辑名称或清除搜索",
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 32.dp),
                    actionLabel = if (searchKeyword.isBlank()) "添加媒体源" else "清除搜索",
                    onAction = { if (searchKeyword.isBlank()) com.lalilu.navigation.AppRouter.route("/media_source").jump() else onClearSearch() },
                )
            }
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
