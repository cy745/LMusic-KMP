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
import androidx.window.core.layout.WindowSizeClass
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
import com.lalilu.lsearch.viewmodel.SearchAction
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
 * 布局（无 Pager，tab 状态由 [SearchVM] 的 [SearchState.typeFilter] 决定）：
 *  - 内容区：根据 [SearchTypeFilter] 渲染单一内容：
 *      - [SearchTypeFilter.All]    →「全部」[LazyVerticalGrid]，每类最多 [ALL_PAGE_ITEM_LIMIT] 个 + 「更多」按钮
 *      - [SearchTypeFilter.Audio]  → 歌曲全量 [LazyColumn]（与歌曲列表页同风格）
 *      - [SearchTypeFilter.Album]  → 专辑全量 [LazyColumn]，行内用 [Row] 平铺（数组按行拆分）
 *      - [SearchTypeFilter.Artist] → 歌手全量 [LazyColumn]（与歌手列表页同风格）
 *  - 底部：悬浮 [SearchTypeTabBar]，选中态绑定 [SearchState.typeFilter]，点击时通过
 *    [SearchAction.SelectType] 切换内容区。
 */
@Composable
fun SearchScreenContent(modifier: Modifier = Modifier) {
    val vm = koinViewModel<SearchVM>()
    val state by vm.state.collectAsState()

    val audios by remember(vm) { vm.audios }.collectAsState(emptyList())
    val albums by remember(vm) { vm.albums }.collectAsState(emptyList())
    val artists by remember(vm) { vm.artists }.collectAsState(emptyList())

    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { 72.dp }
    )
    // 内容区底部需让出 SmartBar + 悬浮 TabRow
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
        when (state.typeFilter) {
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

            SearchTypeFilter.Album -> AlbumRowsTab(
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

        SearchTypeTabBar(
            modifier = Modifier
                .padding(bottom = smartBarHeight())
                .align(Alignment.BottomCenter),
            selected = { state.typeFilter },
            onSelect = { vm.intent(SearchAction.SelectType(it)) }
        )
    }
}

// region: tab pages

/**
 * 「全部」内容：三类内容各展示前 [ALL_PAGE_ITEM_LIMIT] 个，每类带一个「更多」按钮
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

/** 歌曲内容：整行歌曲卡片列表（与歌曲列表页同风格），展示全部结果。 */
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

/**
 * 专辑内容：把专辑数组按当前窗口宽度拆分成若干行数组，每行用 [androidx.compose.foundation.layout.Row]
 * 平铺渲染（元素在当前窗口下的列数分配），展示全部结果。
 */
@Composable
private fun AlbumRowsTab(
    albums: List<LAlbum>,
    keyword: String,
    statusBarTop: Dp,
    bottomPadding: Dp,
) {
    val context = LocalPlatformContext.current
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columns = when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 4
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 3
        else -> 2
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 10.dp,
            end = 10.dp,
            top = statusBarTop,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "album-header") {
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
            item(key = "album-empty") {
                EmptyHint(text = stringResource(Res.string.search_empty_no_results))
            }
            return@LazyColumn
        }

        // 把专辑数组按列数拆分成多行，每行一个 Row（元素均分宽度）
        albums.chunked(columns).forEachIndexed { rowIndex, rowAlbums ->
            item(key = "album-row-$rowIndex") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowAlbums.forEach { album ->
                        AlbumCard(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            album = { album },
                            onClick = { sharedMap ->
                                val coverCacheKey = context.retrieveCacheKey(album)
                                AppRouter.route("/pages/albums/detail")
                                    .with("albumId", album.id)
                                    .with("album", album)
                                    .with("sharedMap", sharedMap)
                                    .with("coverCacheKey", coverCacheKey)
                                    .push()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 歌手内容：整行歌手卡片列表（与歌手列表页同风格），展示全部结果。 */
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
