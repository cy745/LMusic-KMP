package com.lalilu.lsearch.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.lalilu.lsearch.component.SearchKeywordRecommendations
import com.lalilu.lsearch.lsearch.generated.resources.*
import com.lalilu.lsearch.viewmodel.SearchAction
import com.lalilu.lsearch.viewmodel.SearchRecommendationCandidates
import com.lalilu.lsearch.viewmodel.SearchVM
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.smartbar.NavigatorHeader
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** 聚合搜索结果中各内容类型的预览数量上限。 */
private const val ALL_PAGE_AUDIO_LIMIT = 5
private const val ALL_PAGE_ALBUM_LIMIT = 6
private const val ALL_PAGE_ARTIST_LIMIT = 6

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
 * 搜索页面只维护一个 [MultiLayout]，依次聚合展示歌曲、专辑和歌手的限量预览。
 * 完整结果交由各类别已有的列表页展示，并复用列表页自身的关键词搜索能力。
 */
@Composable
fun SearchScreenContent(modifier: Modifier = Modifier) {
    val vm = koinViewModel<SearchVM>()
    val state by vm.state.collectAsState()

    val audios by remember(vm) { vm.audios }.collectAsState()
    val albums by remember(vm) { vm.albums }.collectAsState()
    val artists by remember(vm) { vm.artists }.collectAsState()
    val listState = rememberLazyGridState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 列表开始拖动或惯性滚动时收起软键盘，搜索词、焦点对应的数据和滚动位置均保持不变。
    LaunchedEffect(listState, keyboardController) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling) keyboardController?.hide()
            }
    }

    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { 72.dp }
    )
    // 内容区底部只需让出 SmartBar；类型 TabBar 已移除。
    val bottomPadding = smartBarHeight() + 16.dp
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp

    // 单一结果列表的网格跨越规则（由窗口宽度档位解析）
    val spanRule by adaptiveValue<GridSpanRule>(
        compact = { GridSpanRule.Compact },
        medium = { GridSpanRule.Medium },
        expanded = { GridSpanRule.Expanded }
    )

    SearchResultList(
        modifier = modifier,
        keyword = state.keyword,
        spanRule = spanRule,
        audios = audios,
        albums = albums,
        artists = artists,
        recommendationCandidates = vm.recommendationCandidates,
        onRecommendationClick = { keyword ->
            vm.intent(SearchAction.UpdateKeyword(keyword))
        },
        state = listState,
        statusBarTop = statusBarTop,
        bottomPadding = bottomPadding,
    )
}

/**
 * 聚合搜索结果列表。每种类型只注册预览上限内的元素；结果超过上限时由标题中的“更多”
 * 跳转至对应列表页，并把当前 [keyword] 交给列表页现有的搜索逻辑。
 */
@Composable
private fun SearchResultList(
    modifier: Modifier = Modifier,
    keyword: String,
    spanRule: GridSpanRule,
    audios: List<LAudio>,
    albums: List<LAlbum>,
    artists: List<LArtist>,
    recommendationCandidates: StateFlow<SearchRecommendationCandidates>,
    onRecommendationClick: (String) -> Unit,
    state: LazyGridState,
    statusBarTop: Dp,
    bottomPadding: Dp,
) {
    val visibleAudios = audios.take(ALL_PAGE_AUDIO_LIMIT)
    val visibleAlbums = albums.take(ALL_PAGE_ALBUM_LIMIT)
    val visibleArtists = artists.take(ALL_PAGE_ARTIST_LIMIT)
    val hasVisibleItems = audios.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
    val pageHorizontalPadding = PaddingValues(horizontal = 16.dp)
    val emptyStateHeight = (
        LocalWindowInfo.current.containerDpSize.height - statusBarTop - bottomPadding
    ).coerceAtLeast(0.dp)

    MultiLayout(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(
            top = statusBarTop,
            bottom = bottomPadding
        )
    ) {
        animateItem {
            gap(horizontalGap = 12.dp, verticalGap = 0.5f.dp) {
                if (keyword.isBlank()) {
                    item(
                        key = "search-initial",
                        span = GRID_COLUMNS,
                        paddingValues = pageHorizontalPadding
                    ) {
                        val candidates by recommendationCandidates.collectAsState()
                        SearchKeywordRecommendations(
                            modifier = Modifier.height(emptyStateHeight),
                            title = stringResource(Res.string.search_empty_all),
                            candidates = candidates,
                            onKeywordClick = onRecommendationClick,
                        )
                    }
                    return@gap
                }

                if (!hasVisibleItems) {
                    item(
                        key = "search-empty",
                        span = GRID_COLUMNS,
                        paddingValues = pageHorizontalPadding
                    ) {
                        EmptyHint(
                            modifier = Modifier.height(emptyStateHeight),
                            text = stringResource(Res.string.search_empty_no_results)
                        )
                    }
                    return@gap
                }

                if (audios.isNotEmpty()) {
                    item(
                        key = "all-songs-header",
                        span = GRID_COLUMNS,
                        paddingValues = PaddingValues()
                    ) {
                        SearchResultHeader(
                            title = stringResource(Res.string.search_section_songs),
                            resultCount = audios.size,
                            previewLimit = ALL_PAGE_AUDIO_LIMIT,
                            onMoreClick = {
                                navigateToResultList(route = "/pages/songs", keyword = keyword)
                            }
                        )
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

                if (albums.isNotEmpty()) {
                    item(
                        key = "all-albums-header",
                        span = GRID_COLUMNS,
                        paddingValues = PaddingValues()
                    ) {
                        SearchResultHeader(
                            title = stringResource(Res.string.search_section_albums),
                            resultCount = albums.size,
                            previewLimit = ALL_PAGE_ALBUM_LIMIT,
                            onMoreClick = {
                                navigateToResultList(route = "/pages/albums", keyword = keyword)
                            }
                        )
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

                if (artists.isNotEmpty()) {
                    item(
                        key = "all-artists-header",
                        span = GRID_COLUMNS,
                        paddingValues = PaddingValues()
                    ) {
                        SearchResultHeader(
                            title = stringResource(Res.string.search_section_artists),
                            resultCount = artists.size,
                            previewLimit = ALL_PAGE_ARTIST_LIMIT,
                            onMoreClick = {
                                navigateToResultList(route = "/pages/artists", keyword = keyword)
                            }
                        )
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
}

/** 跳转到对应类型的完整列表，并由目标页使用自身的搜索逻辑处理关键词。 */
private fun navigateToResultList(route: String, keyword: String) {
    AppRouter.route(route)
        .with("keyword", keyword)
        .jump()
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
        onNavigateToDetail = { sharedMap ->
            AppRouter.route("/song/detail")
                .with("mediaId", audio.id)
                .with("song", audio)
                .with("coverCacheKey", coverCacheKey)
                .with("sharedMap", sharedMap)
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
        onClick = { sharedMap ->
            AppRouter.route("/pages/albums/detail")
                .with("albumId", album.id)
                .with("album", album)
                .with("coverCacheKey", coverCacheKey)
                .with("sharedMap", sharedMap)
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
        sharedMapPrefix = "list",
        onClick = { sharedMap ->
            AppRouter.route("/pages/artists/detail")
                .with("artistId", artist.id)
                .with("artist", artist)
                .with("coverCacheKey", coverCacheKey)
                .with("sharedMap", sharedMap)
                .push()
        }
    )
}

// endregion

// region: shared composables

@Composable
private fun SearchResultHeader(
    title: String,
    resultCount: Int,
    previewLimit: Int,
    onMoreClick: () -> Unit
) {
    NavigatorHeader(
        modifier = Modifier.fillMaxWidth(),
        title = title,
        subTitle = stringResource(Res.string.search_result_count, resultCount),
        paddingValues = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 8.dp
        ),
        extraContent = {
            if (resultCount > previewLimit) {
                TextButton(onClick = onMoreClick) {
                    Text(
                        text = stringResource(Res.string.search_more),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@Composable
private fun EmptyHint(
    modifier: Modifier = Modifier,
    text: String
) {
    Box(
        modifier = modifier
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
