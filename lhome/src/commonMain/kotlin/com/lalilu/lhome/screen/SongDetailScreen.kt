package com.lalilu.lhome.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.*
import com.lalilu.RemixIcon
import com.lalilu.common.ext.requestFor
import com.lalilu.lplayer.LPlayer
import org.koin.core.qualifier.named
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.lalilu.adaptive
import com.lalilu.adaptiveValue
import com.lalilu.animated
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.extensions.SharedContext
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.component.SongAlbumInfoCard
import com.lalilu.lhome.screen.detail.MetadataInfos
import com.lalilu.lhome.viewmodel.SongDetailVM
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.AppRouter
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenAction
import com.lalilu.navigation.ScreenActionFactory
import com.lalilu.navigation.smartbar.ActionIconButton
import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.PlaybackMode
import com.lalilu.packed.CoverTitleHeader
import com.lalilu.preview.preview
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.model.albumArtist
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistNames
import com.lalilu.lmedia.domain.model.libraryArtistIds
import com.lalilu.lmedia.domain.model.libraryAlbumId
import com.lalilu.lmedia.domain.model.normalizeLibraryName
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.model.duration
import com.lalilu.lmedia.domain.model.genre
import com.lalilu.lmedia.rememberMediaCoverRequest
import com.lalilu.slotContent
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
@Destination("/song/detail")
data class SongDetailScreen(
    val mediaId: String,
    val song: LAudio? = null,
    val sharedMap: Map<String, String> = emptyMap(),
) : Screen, ScreenActionFactory {
    override val key: String = "${super.key}:$mediaId"

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val playback = LPlayer.instance
        val likeAction = remember(mediaId) {
            requestFor<ScreenAction>(named("like_action")) { parametersOf(mediaId) }
        }
        val addToPlaylistAction = remember(mediaId) {
            requestFor<ScreenAction>(named("add_song_to_playlist_action")) { parametersOf(mediaId) }
        }
        return rememberSongDetailActions(mediaId, playback, likeAction, addToPlaylistAction)
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<SongDetailVM>(parameters = { parametersOf(mediaId) })
        val albumsList by vm.albums.collectAsState(initial = null)
        val artistsList by vm.artists.collectAsState(initial = null)
        val song = vm.songState.value ?: song

        SongDetailScreenContent(
            song = song,
            albums = albumsList.orEmpty(),
            artists = artistsList.orEmpty(),
            sharedMap = sharedMap
        )
    }
}

/** The action instances stay stable; playback observations live inside Dynamic content. */
@Composable
internal fun rememberSongDetailActions(
    mediaId: String,
    playback: Playback,
    likeAction: ScreenAction?,
    addToPlaylistAction: ScreenAction?,
): List<ScreenAction> = remember(mediaId, playback, likeAction, addToPlaylistAction) {
    val next: (com.lalilu.navigation.ActionContext) -> Unit = { PlayerAction.AddToNext(mediaId).action() }
    val play: (com.lalilu.navigation.ActionContext) -> Unit = { PlayerAction.PlayOrToggleById(mediaId).action() }
    listOfNotNull(
        ScreenAction.Dynamic { context ->
            val mode by playback.playbackMode.collectAsState()
            ActionIconButton(
                context = context,
                title = "下一首播放",
                icon = if (mode == PlaybackMode.SHUFFLE) RemixIcon.Editor.insertRowTop
                else RemixIcon.Editor.insertRowBottom,
                onClick = { next(context) },
            )
        },
        addToPlaylistAction,
        ScreenAction.Dynamic { context ->
            val queue by playback.queue.expandedItems.collectAsState()
            val wantsToPlay by playback.wantsToPlay.collectAsState()
            val pause = queue.currentItem()?.playbackId == mediaId && wantsToPlay
            ActionIconButton(
                context = context,
                title = if (pause) "暂停" else "播放",
                icon = if (pause) RemixIcon.Media.pauseFill else RemixIcon.Media.playFill,
                onClick = { play(context) },
            )
        },
        likeAction,
        ScreenAction.DeepLink("next", next),
        ScreenAction.DeepLink("play", play),
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SongDetailScreenContent(
    song: LAudio? = null,
    albums: List<LAlbum> = emptyList(),
    artists: List<LArtist> = emptyList(),
    sharedMap: Map<String, String> = emptyMap(),
) = SharedContext(sharedMap = sharedMap) {
    // Navigation already carries the song metadata. Keep the same rows present while
    // repository subscriptions start, instead of inserting them during the transition.
    val displayedAlbums = remember(song, albums) {
        albums.ifEmpty {
            song?.let { listOf(LAlbum(
                id = it.libraryAlbumId(),
                title = normalizeLibraryName(it.albumName ?: "Unknown"),
                subtitle = it.albumArtist?.let(::normalizeLibraryName).orEmpty(),
            )) }.orEmpty()
        }
    }
    val displayedArtists = remember(song, artists) {
        artists.ifEmpty {
            song?.let { audio ->
                audio.libraryArtistIds().zip(audio.artistNames()).map { (id, name) ->
                    LArtist(id = id, title = name)
                }
            }.orEmpty()
        }
    }
    val coverModel = rememberMediaCoverRequest(song)
    val coverData = coverModel
    val songsInfo = remember(song) {
        val baseInfo = buildMap {
            song?.extra?.let(::putAll)
            song?.artistName?.let { put(LAudioExtraKeys.ArtistName, it) }
            song?.albumName?.let { put(LAudioExtraKeys.AlbumName, it) }
            song?.albumArtist?.let { put(LAudioExtraKeys.AlbumArtist, it) }
            song?.genre?.let { put(LAudioExtraKeys.Genre, it) }
            song?.duration?.takeIf { it > 0L }
                ?.let { put(LAudioExtraKeys.Duration, it.toString()) }
        }
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
                subtitleContent = { modifier ->
                    if (displayedArtists.isNotEmpty()) {
                        FlowRow(
                            modifier = modifier,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            displayedArtists.forEachIndexed { index, artist ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (index > 0) {
                                        Text("·", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 4.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                                    }
                                    val interaction = remember(artist.id) { MutableInteractionSource() }
                                    val hovered by interaction.collectIsHoveredAsState()
                                    val pressed by interaction.collectIsPressedAsState()
                                    Text(
                                        text = artist.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (hovered || pressed) 1f else 0.65f),
                                        modifier = Modifier.clickable(
                                            interactionSource = interaction,
                                            indication = null,
                                            role = Role.Button,
                                        ) {
                                            AppRouter.route("/pages/artists/detail")
                                                .with("artistId", artist.id)
                                                .with("artist", artist)
                                                .push()
                                        }.heightIn(min = 36.dp).wrapContentHeight(),
                                    )
                                }
                            }
                        }
                    } else {
                        Text(song?.artistName.orEmpty(), modifier = modifier,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                extraContent = {
                    val tagContents = listOf("music_tags", "lyrico_tags", "lddc_tags")
                        .mapNotNull { key -> slotContent(key)?.let { key to it } }
                        .toMap()

                    if (tagContents.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tagContents.forEach { (_, content) ->
                                content.ApplyContent(modifier = Modifier) {
                                    "song" composableT { song }
                                }
                            }
                        }
                    }
                }
            )
        }

        items(
            items = displayedAlbums,
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
                onClick = { albumSharedMap ->

                    AppRouter.route("/pages/albums/detail")
                        .with("albumId", album.id)
                        .with("album", album)
                        .with("sharedMap", albumSharedMap)
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
        extra = mapOf(
            LAudioExtraKeys.AlbumName to "ヤバス/りぃふ",
            LAudioExtraKeys.ArtistName to "artist",
            LAudioExtraKeys.AlbumArtist to "albumArtist",
            LAudioExtraKeys.Composer to "composer",
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
