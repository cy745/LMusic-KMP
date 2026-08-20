package com.lalilu.lsearch.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggerItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import com.lalilu.adaptiveValue
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.extensions.retrieveCacheKey
import com.lalilu.lalbum.component.AlbumCard
import com.lalilu.lartist.component.ArtistCard
import com.lalilu.lmedia.component.AudioItemCard
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lsearch.component.SearchTypeTabBar
import com.lalilu.lsearch.lsearch.generated.resources.*
import com.lalilu.lsearch.viewmodel.SearchTypeFilter
import com.lalilu.lsearch.viewmodel.SearchVM
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.smartbar.NavigatorHeader
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Height of the floating type-tab row; must match SearchTypeTabBar default. */
private val TAB_ROW_HEIGHT = 56.dp

/** 全部 Tab 中每个内容类型最多展示的元素个数。 */
private const val ALL_PAGE_ITEM_LIMIT = 10

/** 全部 Tab 网格的固定列数。 */
private const val GRID_COLUMNS = 12

/**
 * 每种内容类型在「全部」Tab 网格中的 span 除数。
 *
 * 各项的实际 span = `maxLineSpan / divisor`（其中 maxLineSpan == [GRID_COLUMNS]）。
 *
 * 按设备档位（窗口宽度）取不同规则：
 *  - [Compact] 手机：歌曲/歌手占整行（divisor = 1），专辑占半行（divisor = 2）
 *  - [Medium] 平板：歌曲/歌手占半行（divisor = 2），专辑占 1/3（divisor = 3）
 *  - [Expanded] 大屏：歌曲/歌手占 1/3（divisor = 3），专辑占 1/4（divisor = 4）
 */
private data class GridSpanRule(
    val audioDivisor: Int,
    val artistDivisor: Int,
    val albumDivisor: Int,
) {
    companion object {
        val Compact = GridSpanRule(
            audioDivisor = 1,
            artistDivisor = 1,
            albumDivisor = 2,
        )
        val Medium = GridSpanRule(
            audioDivisor = 2,
            artistDivisor = 2,
            albumDivisor = 3,
        )
        val Expanded = GridSpanRule(
            audioDivisor = 3,
            artistDivisor = 3,
            albumDivisor = 4,
        )
    }
}

// region: span helpers (evaluated inside LazyGridItemSpanScope, maxLineSpan == GRID_COLUMNS)

private fun LazyGridItemSpanScope.audioSpan(rule: GridSpanRule) =
    GridItemSpan(maxLineSpan / rule.audioDivisor)

private fun LazyGridItemSpanScope.artistSpan(rule: GridSpanRule) =
    GridItemSpan(maxLineSpan / rule.artistDivisor)

private fun LazyGridItemSpanScope.albumSpan(rule: GridSpanRule) =
    GridItemSpan(maxLineSpan / rule.albumDivisor)

private fun LazyGridItemSpanScope.fullSpan(): GridItemSpan = GridItemSpan(maxLineSpan)

// endregion

/**
 * Content for [SearchScreen].
 *
 * A [HorizontalPager] hosts four independently-scrolling pages, one per
 * [SearchTypeFilter]:
 *  - All     → [LazyVerticalGrid] preview, at most [ALL_PAGE_ITEM_LIMIT] per type + a "more" button
 *  - Audio   → [LazyColumn] of song rows (styled like detail pages)
 *  - Album   → [LazyVerticalStaggeredGrid] of album cards (styled like AlbumsScreen)
 *  - Artist  → [LazyColumn] of artist rows (styled like ArtistsScreen)
 *
 * Each page owns its own lazy-list state, so switching tabs / swiping pages does
 * NOT share or disturb the other pages' scroll positions. The floating
 * [SearchTypeTabBar] is anchored to the bottom of the surrounding Box and syncs
 * with [androidx.compose.foundation.pager.PagerState.currentPage].
 */
@Composable
fun SearchScreenContent(modifier: Modifier = Modifier) {
    val vm = koinViewModel<SearchVM>()
    val state by vm.state.collectAsState()

    val audios by remember(vm) { vm.audios }.collectAsState(emptyList())
    val albums by remember(vm) { vm.albums }.collectAsState(emptyList())
    val artists by remember(vm) { vm.artists }.collectAsState(emptyList())

    val pagerState = rememberPagerState(pageCount = { SearchTypeFilter.entries.size })
    val scope = rememberCoroutineScope()

    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { 72.dp }
    )
    // 每个分页内容区底部需让出 SmartBar + 悬浮 TabRow
    val bottomPadding = smartBarHeight() + TAB_ROW_HEIGHT + 16.dp
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp

    // 全部 Tab 的网格跨越规则（由窗口宽度档位解析）
    val spanRule by adaptiveValue<GridSpanRule>(
        compact = { GridSpanRule.Compact },
        medium = { GridSpanRule.Medium },
        expanded = { GridSpanRule.Expanded }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val filter = SearchTypeFilter.entries[page]
            when (filter) {
                SearchTypeFilter.All -> AllSearchTab(
                    spanRule = spanRule,
                    audios = audios,
                    albums = albums,
                    artists = artists,
                    keywordBlank = state.keyword.isBlank(),
                    statusBarTop = statusBarTop,
                    bottomPadding = bottomPadding,
                )

                SearchTypeFilter.Audio -> AudioSearchTab(
                    audios = audios,
                    keyword = state.keyword,
                    statusBarTop = statusBarTop,
                    bottomPadding = bottomPadding,
                )

                SearchTypeFilter.Album -> AlbumSearchTab(
                    albums = albums,
                    keyword = state.keyword,
                    statusBarTop = statusBarTop,
                    bottomPadding = bottomPadding,
                )

                SearchTypeFilter.Artist -> ArtistSearchTab(
                    artists = artists,
                    keyword = state.keyword,
                    statusBarTop = statusBarTop,
                    bottomPadding = bottomPadding,
                )
            }
        }

        SearchTypeTabBar(
            modifier = Modifier
                .padding(bottom = smartBarHeight())
                .align(Alignment.BottomCenter),
            selected = { SearchTypeFilter.entries[pagerState.currentPage] },
            onSelect = { filter ->
                scope.launch {
                    pagerState.animateScrollToPage(SearchTypeFilter.entries.indexOf(filter))
                }
            }
        )
    }
}

// region: tab pages

/**
 * 「全部」分页：三类内容各展示前 [ALL_PAGE_ITEM_LIMIT] 个，每类带一个「更多」按钮
 * 跳转到对应的独立列表页（/pages/songs、/pages/albums、/pages/artists）。
 */
@Composable
private fun AllSearchTab(
    spanRule: GridSpanRule,
    audios: List<LAudio>,
    albums: List<LAlbum>,
    artists: List<LArtist>,
    keywordBlank: Boolean,
    statusBarTop: Dp,
    bottomPadding: Dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = statusBarTop,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (keywordBlank && audios.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            item(span = { fullSpan() }) {
                EmptyHint(text = stringResource(Res.string.search_empty_all))
            }
            return@LazyVerticalGrid
        }

        if (audios.isNotEmpty()) {
            item(key = "all-songs-header", span = { fullSpan() }) {
                SectionHeaderWithMore(
                    title = stringResource(Res.string.search_section_songs),
                    onMoreClick = { AppRouter.route("/pages/songs").push() }
                )
            }
            gridItems(
                items = audios.take(ALL_PAGE_ITEM_LIMIT),
                key = { "audio-${it.id}" },
                span = { audioSpan(spanRule) }
            ) { audio ->
                AudioCardItem(audio = audio, allAudios = audios)
            }
        }

        if (albums.isNotEmpty()) {
            item(key = "all-albums-header", span = { fullSpan() }) {
                SectionHeaderWithMore(
                    title = stringResource(Res.string.search_section_albums),
                    onMoreClick = { AppRouter.route("/pages/albums").push() }
                )
            }
            gridItems(
                items = albums.take(ALL_PAGE_ITEM_LIMIT),
                key = { "album-${it.id}" },
                span = { albumSpan(spanRule) }
            ) { album ->
                AlbumCardItem(album = album)
            }
        }

        if (artists.isNotEmpty()) {
            item(key = "all-artists-header", span = { fullSpan() }) {
                SectionHeaderWithMore(
                    title = stringResource(Res.string.search_section_artists),
                    onMoreClick = { AppRouter.route("/pages/artists").push() }
                )
            }
            gridItems(
                items = artists.take(ALL_PAGE_ITEM_LIMIT),
                key = { "artist-${it.id}" },
                span = { artistSpan(spanRule) }
            ) { artist ->
                ArtistCardItem(artist = artist)
            }
        }

        if (audios.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            item(span = { fullSpan() }) {
                EmptyHint(text = stringResource(Res.string.search_empty_no_results))
            }
        }
    }
}

/** 歌曲分页：整行歌曲卡片列表（与歌曲详情页同风格）。 */
@Composable
private fun AudioSearchTab(
    audios: List<LAudio>,
    keyword: String,
    statusBarTop: Dp,
    bottomPadding: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBarTop,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "audio-header") {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.search_filter_audio),
                subTitle = if (keyword.isBlank()) {
                    "共 ${audios.size} 首歌曲"
                } else {
                    "搜索: $keyword (${audios.size} 首)"
                }
            )
        }
        if (audios.isEmpty()) {
            item(key = "audio-empty") {
                EmptyHint(text = stringResource(Res.string.search_empty_no_results))
            }
            return@LazyColumn
        }
        listItems(
            items = audios,
            key = { it.id },
            contentType = { LAudio::class }
        ) { audio ->
            AudioCardItem(audio = audio, allAudios = audios)
        }
    }
}

/** 专辑分页：专辑卡片瀑布流（与专辑列表页同风格）。 */
@Composable
private fun AlbumSearchTab(
    albums: List<LAlbum>,
    keyword: String,
    statusBarTop: Dp,
    bottomPadding: Dp,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columns = if (windowSizeClass.windowWidthSizeClass.toString().contains("Expanded")) 3 else 2

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 10.dp,
            end = 10.dp,
            top = statusBarTop,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        item {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.search_filter_album),
                subTitle = if (keyword.isBlank()) {
                    "共 ${albums.size} 张专辑"
                } else {
                    "搜索: $keyword (${albums.size} 张专辑)"
                }
            )
        }
        if (albums.isEmpty()) {
            item {
                EmptyHint(text = stringResource(Res.string.search_empty_no_results))
            }
            return@LazyVerticalStaggeredGrid
        }
        staggerItems(
            items = albums,
            key = { it.id },
            contentType = { LAlbum::class }
        ) { album ->
            AlbumCardItem(album = album)
        }
    }
}

/** 歌手分页：整行歌手卡片列表（与歌手列表页同风格）。 */
@Composable
private fun ArtistSearchTab(
    artists: List<LArtist>,
    keyword: String,
    statusBarTop: Dp,
    bottomPadding: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBarTop,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "artist-header") {
            NavigatorHeader(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.search_filter_artist),
                subTitle = if (keyword.isBlank()) {
                    "共 ${artists.size} 位歌手"
                } else {
                    "搜索: $keyword (${artists.size} 位歌手)"
                }
            )
        }
        if (artists.isEmpty()) {
            item(key = "artist-empty") {
                EmptyHint(text = stringResource(Res.string.search_empty_no_results))
            }
            return@LazyColumn
        }
        listItems(
            items = artists,
            key = { it.id },
            contentType = { LArtist::class }
        ) { artist ->
            ArtistCardItem(artist = artist)
        }
    }
}

// endregion

// region: card items

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioCardItem(audio: LAudio, allAudios: List<LAudio>) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val coverCacheKey = remember(audio) { context.retrieveCacheKey(audio) }

    AudioItemCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.5.dp),
        id = audio.id,
        title = audio.title,
        subtitle = audio.subtitle,
        imageData = audio,
        onPlay = {
            scope.launch {
                PlayerAction.UpdateList(
                    ids = allAudios.map { it.id },
                    id = audio.id,
                    start = true
                ).action()
            }
        },
        onNavigateToDetail = { _ ->
            AppRouter.route("/song/detail")
                .with("mediaId", audio.id)
                .with("song", audio)
                .with("coverCacheKey", coverCacheKey)
                .jump()
        }
    )
}

@Composable
private fun AlbumCardItem(album: LAlbum) {
    val context = LocalPlatformContext.current
    val coverCacheKey = remember(album) { context.retrieveCacheKey(album) }

    AlbumCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.5.dp),
        album = { album },
        onClick = { _ ->
            AppRouter.route("/pages/albums/detail")
                .with("albumId", album.id)
                .with("album", album)
                .with("coverCacheKey", coverCacheKey)
                .push()
        }
    )
}

@Composable
private fun ArtistCardItem(artist: LArtist) {
    val context = LocalPlatformContext.current
    val coverCacheKey = remember(artist) { context.retrieveCacheKey(artist) }

    ArtistCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.5.dp),
        artist = artist,
        onClick = { _ ->
            AppRouter.route("/pages/artists/detail")
                .with("artistId", artist.id)
                .with("artist", artist)
                .with("coverCacheKey", coverCacheKey)
                .push()
        }
    )
}

// endregion

// region: shared composables

@Composable
private fun SectionHeaderWithMore(
    title: String,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        TextButton(onClick = onMoreClick) {
            Text(
                text = stringResource(Res.string.search_more),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
    }
}

// endregion
