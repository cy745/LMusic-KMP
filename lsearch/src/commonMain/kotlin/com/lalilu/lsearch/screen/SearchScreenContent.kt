package com.lalilu.lsearch.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import com.lalilu.adaptiveValue
import com.lalilu.component.MultiLayout
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** 底部悬浮类型 Tab 的高度，需要与 [SearchTypeTabBar] 的默认高度保持一致。 */
private val TAB_ROW_HEIGHT = 56.dp

/** 全部 Tab 中每个内容类型最多展示的元素个数。 */
private const val ALL_PAGE_ITEM_LIMIT = 10

/** [MultiLayout] 使用的固定逻辑列数。 */
private const val GRID_COLUMNS = 12

/**
 * 每种内容类型在搜索结果列表中的 span 除数。
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

/**
 * 搜索页面始终只维护一个 [MultiLayout] 列表，Tab 不再切换不同的 Lazy 容器，而是控制同一个
 * 列表本轮注册哪些类型的 item：
 * - [SearchTypeFilter.All]：依次显示歌曲、专辑和歌手，每类最多 [ALL_PAGE_ITEM_LIMIT] 条；
 * - 其他类型：只显示对应类型的全部结果。
 *
 * 这种结构会复用同一个 LazyGridState 与布局容器，避免不同 Tab 各自维护列表和滚动实现。
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

    // 单一结果列表的网格跨越规则（由窗口宽度档位解析）
    val spanRule by adaptiveValue<GridSpanRule>(
        compact = { GridSpanRule.Compact },
        medium = { GridSpanRule.Medium },
        expanded = { GridSpanRule.Expanded }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        SearchResultList(
            typeFilter = state.typeFilter,
            spanRule = spanRule,
            audios = audios,
            albums = albums,
            artists = artists,
            keywordBlank = state.keyword.isBlank(),
            statusBarTop = statusBarTop,
            bottomPadding = bottomPadding,
        )

        SearchTypeTabBar(
            modifier = Modifier
                .align(Alignment.BottomCenter),
            selected = { state.typeFilter },
            onSelect = { vm.intent(SearchAction.SelectType(it)) },
            paddingBottom = smartBarHeight
        )
    }
}

/**
 * 单一搜索结果列表。
 *
 * [typeFilter] 只控制向同一个 [MultiLayout] 注册的元素类型。「全部」包含分区标题和“更多”
 * 入口，单类型 Tab 不再创建另一套列表，仅注册该类型的全部卡片。
 */
@Composable
private fun SearchResultList(
    typeFilter: SearchTypeFilter,
    spanRule: GridSpanRule,
    audios: List<LAudio>,
    albums: List<LAlbum>,
    artists: List<LArtist>,
    keywordBlank: Boolean,
    statusBarTop: Dp,
    bottomPadding: Dp,
) {
    val showAudios = typeFilter == SearchTypeFilter.All || typeFilter == SearchTypeFilter.Audio
    val showAlbums = typeFilter == SearchTypeFilter.All || typeFilter == SearchTypeFilter.Album
    val showArtists = typeFilter == SearchTypeFilter.All || typeFilter == SearchTypeFilter.Artist
    val visibleAudios = if (typeFilter == SearchTypeFilter.All) {
        audios.take(ALL_PAGE_ITEM_LIMIT)
    } else {
        audios
    }
    val visibleAlbums = if (typeFilter == SearchTypeFilter.All) {
        albums.take(ALL_PAGE_ITEM_LIMIT)
    } else {
        albums
    }
    val visibleArtists = if (typeFilter == SearchTypeFilter.All) {
        artists.take(ALL_PAGE_ITEM_LIMIT)
    } else {
        artists
    }
    val hasVisibleItems = when (typeFilter) {
        SearchTypeFilter.All -> audios.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
        SearchTypeFilter.Audio -> audios.isNotEmpty()
        SearchTypeFilter.Album -> albums.isNotEmpty()
        SearchTypeFilter.Artist -> artists.isNotEmpty()
    }
    val pageHorizontalPadding = PaddingValues(horizontal = 16.dp)

    MultiLayout(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBarTop,
            bottom = bottomPadding
        )
    ) {
        gap(horizontalGap = 12.dp, verticalGap = 0.5f.dp) {
            if (!hasVisibleItems) {
                item(
                    key = "search-empty-${typeFilter.name}",
                    span = GRID_COLUMNS,
                    paddingValues = pageHorizontalPadding
                ) {
                    val showInitialHint = typeFilter == SearchTypeFilter.All && keywordBlank
                    EmptyHint(
                        text = stringResource(
                            if (showInitialHint) Res.string.search_empty_all
                            else Res.string.search_empty_no_results
                        )
                    )
                }
                return@gap
            }

            if (showAudios && audios.isNotEmpty()) {
                if (typeFilter == SearchTypeFilter.All) {
                    item(
                        key = "all-songs-header",
                        span = GRID_COLUMNS,
                        paddingValues = pageHorizontalPadding
                    ) {
                        SectionHeaderWithMore(
                            title = stringResource(Res.string.search_section_songs),
                            onMoreClick = { AppRouter.route("/pages/songs").push() }
                        )
                    }
                }
                items(
                    items = visibleAudios,
                    key = { _, audio -> "audio-${audio.id}" },
                    contentType = { _, _ -> LAudio::class },
                    span = GRID_COLUMNS / spanRule.audioDivisor,
                    paddingValues = PaddingValues()
                ) { _, audio ->
                    AudioCardItem(audio = audio, allAudios = audios)
                }
            }

            if (showAlbums && albums.isNotEmpty()) {
                if (typeFilter == SearchTypeFilter.All) {
                    item(
                        key = "all-albums-header",
                        span = GRID_COLUMNS,
                        paddingValues = pageHorizontalPadding
                    ) {
                        SectionHeaderWithMore(
                            title = stringResource(Res.string.search_section_albums),
                            onMoreClick = { AppRouter.route("/pages/albums").push() }
                        )
                    }
                }
                items(
                    items = visibleAlbums,
                    key = { _, album -> "album-${album.id}" },
                    contentType = { _, _ -> LAlbum::class },
                    span = GRID_COLUMNS / spanRule.albumDivisor,
                    paddingValues = pageHorizontalPadding
                ) { _, album ->
                    AlbumCardItem(album = album)
                }
            }

            if (showArtists && artists.isNotEmpty()) {
                if (typeFilter == SearchTypeFilter.All) {
                    item(
                        key = "all-artists-header",
                        span = GRID_COLUMNS,
                        paddingValues = pageHorizontalPadding
                    ) {
                        SectionHeaderWithMore(
                            title = stringResource(Res.string.search_section_artists),
                            onMoreClick = { AppRouter.route("/pages/artists").push() }
                        )
                    }
                }
                items(
                    items = visibleArtists,
                    key = { _, artist -> "artist-${artist.id}" },
                    contentType = { _, _ -> LArtist::class },
                    span = GRID_COLUMNS / spanRule.artistDivisor,
                    paddingValues = PaddingValues()
                ) { _, artist ->
                    ArtistCardItem(artist = artist)
                }
            }
        }
    }
}

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
