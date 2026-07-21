package com.lalilu.lhome.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.lalilu.adaptive
import com.lalilu.adaptiveValue
import com.lalilu.animated
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.extensions.SharedContext
import com.lalilu.extensions.retrieveCacheKey
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.component.SongAlbumInfoCard
import com.lalilu.lhome.screen.detail.MetadataInfos
import com.lalilu.lhome.viewmodel.SongDetailVM
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenAction
import com.lalilu.navigation.ScreenActionFactory
import com.lalilu.packed.CoverTitleHeader
import com.lalilu.preview.preview
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.Metadata as DomainMetadata
import com.lalilu.slotContent
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
@Destination("/song/detail")
data class SongDetailScreen(
    val mediaId: String,
    val song: LAudio? = null,
    val coverCacheKey: String? = null,
    val sharedMap: Map<String, String> = emptyMap(),
) : Screen, ScreenActionFactory {
    override val key: String = "${super.key}:$mediaId"

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val vm = koinViewModel<SongDetailVM>(parameters = { parametersOf(mediaId) })

        return remember {
            listOf(
                ScreenAction.Static(
                    title = { "下一首" },
                    onAction = { PlayerAction.SkipToNext.action() }
                ),
                ScreenAction.Static(
                    title = { "播放" },
                    onAction = { PlayerAction.PlayById(mediaId).action() }
                ),
            )
        }
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<SongDetailVM>(parameters = { parametersOf(mediaId) })
        val albumsList by vm.albums.collectAsState(initial = null)
        val artistsList by vm.artists.collectAsState(initial = null)
        val song = vm.songState.value ?: song

        SongDetailScreenContent(
            song = song,
            albums = (albumsList ?: emptyList()).ifEmpty { emptyList() },
            artists = (artistsList ?: emptyList()).ifEmpty { emptyList() },
            coverCacheKey = coverCacheKey,
            sharedMap = sharedMap
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SongDetailScreenContent(
    song: LAudio? = null,
    albums: List<LAlbum> = emptyList(),
    artists: List<LArtist> = emptyList(),
    coverCacheKey: String? = null,
    sharedMap: Map<String, String> = emptyMap(),
) = SharedContext(sharedMap = sharedMap) {
    val context = LocalPlatformContext.current
    val coverData = remember(song) {
        ImageRequest.Builder(context)
            .placeholderMemoryCacheKey(coverCacheKey)
            .data(song)
            .build()
    }
    val songsInfo = remember(song) {
        val baseInfo = (song?.extra ?: emptyMap()) + (song?.metadata?.toMap() ?: emptyMap())
        val withSource = song?.mediaSourceName?.takeIf { it.isNotBlank() }
            ?.let { baseInfo + ("数据源" to it) }
            ?: baseInfo
        withSource.filter { it.value.isNotBlank() }
    }

    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = smartBarHeight() + 16.dp),
    ) {
        item {
            CoverTitleHeader(
                coverData = coverData,
                title = song?.title ?: "Unknown",
                subtitle = song?.subtitle,
                extraContent = {
                    val tagContents = listOf("music_tags", "lddc_tags")
                        .mapNotNull { key -> slotContent(key)?.let { key to it } }
                        .toMap()

                    if (tagContents.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tagContents.forEach { (key, content) ->
                                content.ApplyContent(modifier = Modifier) {
                                    if (key == "music_tags") {
                                        "song" composableT { song }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        items(
            items = albums,
            key = { it.id }
        ) { album ->
            val paddingHorizontal = adaptiveValue(
                compact = { 16.dp },
                medium = { 40.dp }
            ).animated()

            val adaptiveWidth = adaptiveValue(
                compact = { WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp },
                medium = { 450.dp }
            ).animated()

            SongAlbumInfoCard(
                modifier = Modifier
                    .width(width = adaptiveWidth.value)
                    .adaptive(
                        compact = { fillMaxWidth() },
                        medium = { this }
                    )
                    .padding(top = 24.dp)
                    .padding(horizontal = paddingHorizontal.value),
                album = album,
                onClick = {
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

        item {
            MetadataInfos(
                modifier = Modifier.fillMaxWidth(),
                metadata = songsInfo
            )
        }
    }
}


@Preview
@Composable
private fun SongDetailScreenContentPreview() = preview {
    enableNetworkImage()
    setFallbackUrl("https://www.dmoe.cc/random.php")

    val artists = listOf(
        LArtist(
            id = "id",
            title = "ヤバス",
            subtitle = "Yabus",
            extra = mapOf(),
        ),
        LArtist(
            id = "id",
            title = "リィフ",
            subtitle = "Ryu",
            extra = mapOf(),
        )
    )

    val album = LAlbum(
        id = "id",
        title = "ヤバス",
        subtitle = "Yabus",
        extra = mapOf(),
    )

    val song = LAudio(
        id = "id",
        title = "ライアーメイデン (feat. りぃふ)",
        subtitle = "ヤバス/りぃふ",
        extra = mapOf(),
        metadata = DomainMetadata(
            title = "ライアーメイデン (feat. りぃふ)",
            album = "ヤバス/りぃふ",
            artist = "artist",
            albumArtist = "albumArtist",
            composer = "composer",
            lyricist = "",
            comment = "",
            genre = "",
            track = "",
            disc = "",
            date = "",
            duration = 0,
            dateAdded = 0,
            dateModified = 0
        ),
        mediaSourceName = ""
    )

    // song.link(artists[0]) - removed with entity Linkable
    // song.link(artists[1]) - removed
    // song.link(album) - removed

    SongDetailScreenContent(song = song)
}

@Preview(device = Devices.TABLET)
@Composable
private fun SongDetailScreenContentPreviewForPad() {
    SongDetailScreenContentPreview()
}