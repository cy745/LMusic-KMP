package com.lalilu.lsearch.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.lalilu.lsearch.viewmodel.SearchAction
import com.lalilu.lsearch.viewmodel.SearchTypeFilter
import com.lalilu.lsearch.viewmodel.SearchVM
import com.lalilu.navigation.AppRouter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Height of the floating type-tab row; must match SearchTypeTabBar default. */
private val TAB_ROW_HEIGHT = 56.dp

/** 搜索结果网格的固定列数。 */
private const val GRID_COLUMNS = 12

/**
 * 每种内容类型在网格中的 span 除数。
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
 * Content for [SearchScreen]. Rendered inside the Screen's main content slot.
 *
 * Layout:
 *  - A 12-column [LazyVerticalGrid] renders results; each row's span depends on
 *    the [GridSpanRule] resolved from the window width class.
 *  - The outer Box does NOT reserve the SmartBar space; instead the floating
 *    [SearchTypeTabBar] pads itself up by the SmartBar height, and the grid's
 *    bottom contentPadding accounts for `smartBar + tabRow` so scrolled content
 *    clears both.
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

    // resolver custom: 由 adaptiveValue 按窗口宽度档位提供 GridSpanRule
    val spanRule by adaptiveValue<GridSpanRule>(
        compact = { GridSpanRule.Compact },
        medium = { GridSpanRule.Medium },
        expanded = { GridSpanRule.Expanded }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = smartBarHeight() + TAB_ROW_HEIGHT + 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state.typeFilter) {
                SearchTypeFilter.All -> renderAllTab(
                    spanRule = spanRule,
                    audios = audios,
                    albums = albums,
                    artists = artists,
                    isKeywordEmpty = state.keyword.isBlank()
                )

                SearchTypeFilter.Audio -> renderAudioTab(spanRule, audios)
                SearchTypeFilter.Album -> renderAlbumTab(spanRule, albums)
                SearchTypeFilter.Artist -> renderArtistTab(spanRule, artists)
            }
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

// region: render branches

private fun LazyGridScope.renderAllTab(
    spanRule: GridSpanRule,
    audios: List<LAudio>,
    albums: List<LAlbum>,
    artists: List<LArtist>,
    isKeywordEmpty: Boolean
) {
    if (isKeywordEmpty && audios.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
        item(span = { fullSpan() }) {
            EmptyHint(text = stringResource(Res.string.search_empty_all))
        }
        return
    }

    if (audios.isNotEmpty()) {
        item(key = "audios-header", span = { fullSpan() }) {
            SectionHeader(text = stringResource(Res.string.search_section_songs))
        }
        items(
            items = audios,
            key = { "audio-${it.id}" },
            span = { audioSpan(spanRule) }
        ) { audio ->
            AudioGridItem(audio = audio, allAudios = audios)
        }
    }

    if (albums.isNotEmpty()) {
        item(key = "albums-header", span = { fullSpan() }) {
            SectionHeader(text = stringResource(Res.string.search_section_albums))
        }
        items(
            items = albums,
            key = { "album-${it.id}" },
            span = { albumSpan(spanRule) }
        ) { album ->
            AlbumGridItem(album = album)
        }
    }

    if (artists.isNotEmpty()) {
        item(key = "artists-header", span = { fullSpan() }) {
            SectionHeader(text = stringResource(Res.string.search_section_artists))
        }
        items(
            items = artists,
            key = { "artist-${it.id}" },
            span = { artistSpan(spanRule) }
        ) { artist ->
            ArtistGridItem(artist = artist)
        }
    }

    if (audios.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
        item(span = { fullSpan() }) {
            EmptyHint(text = stringResource(Res.string.search_empty_no_results))
        }
    }
}

private fun LazyGridScope.renderAudioTab(spanRule: GridSpanRule, audios: List<LAudio>) {
    if (audios.isEmpty()) {
        item(span = { fullSpan() }) {
            EmptyHint(text = stringResource(Res.string.search_empty_no_results))
        }
        return
    }
    items(
        items = audios,
        key = { "audio-${it.id}" },
        span = { audioSpan(spanRule) }
    ) { audio ->
        AudioGridItem(audio = audio, allAudios = audios)
    }
}

private fun LazyGridScope.renderAlbumTab(spanRule: GridSpanRule, albums: List<LAlbum>) {
    if (albums.isEmpty()) {
        item(span = { fullSpan() }) {
            EmptyHint(text = stringResource(Res.string.search_empty_no_results))
        }
        return
    }
    items(
        items = albums,
        key = { "album-${it.id}" },
        span = { albumSpan(spanRule) }
    ) { album ->
        AlbumGridItem(album = album)
    }
}

private fun LazyGridScope.renderArtistTab(spanRule: GridSpanRule, artists: List<LArtist>) {
    if (artists.isEmpty()) {
        item(span = { fullSpan() }) {
            EmptyHint(text = stringResource(Res.string.search_empty_no_results))
        }
        return
    }
    items(
        items = artists,
        key = { "artist-${it.id}" },
        span = { artistSpan(spanRule) }
    ) { artist ->
        ArtistGridItem(artist = artist)
    }
}

// endregion

// region: grid items

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioGridItem(audio: LAudio, allAudios: List<LAudio>) {
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
private fun AlbumGridItem(album: LAlbum) {
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
private fun ArtistGridItem(artist: LArtist) {
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )
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
