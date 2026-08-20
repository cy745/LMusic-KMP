package com.lalilu.lsearch.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
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
import com.lalilu.lsearch.lsearch.generated.resources.Res
import com.lalilu.lsearch.lsearch.generated.resources.search_empty_all
import com.lalilu.lsearch.lsearch.generated.resources.search_empty_no_results
import com.lalilu.lsearch.lsearch.generated.resources.search_section_albums
import com.lalilu.lsearch.lsearch.generated.resources.search_section_artists
import com.lalilu.lsearch.lsearch.generated.resources.search_section_songs
import com.lalilu.lsearch.viewmodel.SearchAction
import com.lalilu.lsearch.viewmodel.SearchTypeFilter
import com.lalilu.lsearch.viewmodel.SearchVM
import com.lalilu.navigation.AppRouter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Height of the floating type-tab row; must match SearchTypeTabBar default. */
private val TAB_ROW_HEIGHT = 56.dp

/**
 * Content for [SearchScreen]. Rendered inside the Screen's main content slot.
 *
 * The layout is a [Box] that overlays a floating [SearchTypeTabBar] (anchored
 * to BottomCenter) over a [LazyColumn] of results. The LazyColumn's bottom
 * contentPadding accounts for the tab row + smart bar height so content never
 * sits underneath the floating UI.
 */
@OptIn(ExperimentalFoundationApi::class)
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = TAB_ROW_HEIGHT + smartBarHeight() + 16.dp
            )
        ) {
            when (state.typeFilter) {
                SearchTypeFilter.All -> renderAllTab(
                    audios = audios,
                    albums = albums,
                    artists = artists,
                    isKeywordEmpty = state.keyword.isBlank()
                )
                SearchTypeFilter.Audio -> renderAudioTab(audios)
                SearchTypeFilter.Album -> renderAlbumTab(albums)
                SearchTypeFilter.Artist -> renderArtistTab(artists)
            }
        }

        SearchTypeTabBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            selected = { state.typeFilter },
            onSelect = { vm.intent(SearchAction.SelectType(it)) }
        )
    }
}

// region: render branches

private fun LazyListScope.renderAllTab(
    audios: List<LAudio>,
    albums: List<LAlbum>,
    artists: List<LArtist>,
    isKeywordEmpty: Boolean
) {
    if (isKeywordEmpty && audios.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
        item("empty") { EmptyHint(text = stringResource(Res.string.search_empty_all)) }
        return
    }

    if (audios.isNotEmpty()) {
        item("audios-header") {
            SectionHeader(text = stringResource(Res.string.search_section_songs))
        }
        items(audios, key = { "audio-${it.id}" }) { audio ->
            AudioListRow(audio = audio, allAudios = audios)
        }
    }

    if (albums.isNotEmpty()) {
        item("albums-header") {
            SectionHeader(text = stringResource(Res.string.search_section_albums))
        }
        items(albums, key = { "album-${it.id}" }) { album ->
            AlbumListRow(album = album)
        }
    }

    if (artists.isNotEmpty()) {
        item("artists-header") {
            SectionHeader(text = stringResource(Res.string.search_section_artists))
        }
        items(artists, key = { "artist-${it.id}" }) { artist ->
            ArtistListRow(artist = artist)
        }
    }

    if (audios.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
        item("empty") { EmptyHint(text = stringResource(Res.string.search_empty_no_results)) }
    }
}

private fun LazyListScope.renderAudioTab(audios: List<LAudio>) {
    if (audios.isEmpty()) {
        item("empty") { EmptyHint(text = stringResource(Res.string.search_empty_no_results)) }
        return
    }
    items(audios, key = { "audio-${it.id}" }) { audio ->
        AudioListRow(audio = audio, allAudios = audios)
    }
}

private fun LazyListScope.renderAlbumTab(albums: List<LAlbum>) {
    if (albums.isEmpty()) {
        item("empty") { EmptyHint(text = stringResource(Res.string.search_empty_no_results)) }
        return
    }
    items(albums, key = { "album-${it.id}" }) { album ->
        AlbumListRow(album = album)
    }
}

private fun LazyListScope.renderArtistTab(artists: List<LArtist>) {
    if (artists.isEmpty()) {
        item("empty") { EmptyHint(text = stringResource(Res.string.search_empty_no_results)) }
        return
    }
    items(artists, key = { "artist-${it.id}" }) { artist ->
        ArtistListRow(artist = artist)
    }
}

// endregion

// region: list rows

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioListRow(audio: LAudio, allAudios: List<LAudio>) {
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
private fun AlbumListRow(album: LAlbum) {
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
private fun ArtistListRow(artist: LArtist) {
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
            .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
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