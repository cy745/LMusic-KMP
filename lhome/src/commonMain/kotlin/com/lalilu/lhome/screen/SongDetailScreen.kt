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
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.component.SongAlbumInfoCard
import com.lalilu.lhome.screen.detail.MetadataInfos
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.*
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.Screen
import com.lalilu.navigation.ScreenAction
import com.lalilu.navigation.ScreenActionFactory
import com.lalilu.packed.CoverHeader
import com.lalilu.preview.preview
import kotlinx.serialization.Serializable

@Serializable
@Destination("/song/detail")
data class SongDetailScreen(
    val mediaId: String,
    val song: LAudio? = null,
    val coverCacheKey: String? = null,
    val sharedMap: Map<String, String> = emptyMap(),
) : Screen, ScreenActionFactory {
    override val key: String = "${super.key}_$mediaId"

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
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
        val song by remember { LMedia.instance.flow<LAudio>(id = mediaId) }
            .collectAsState(song)

        SongDetailScreenContent(
            song = song,
            coverCacheKey = coverCacheKey,
            sharedMap = sharedMap
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SongDetailScreenContent(
    song: LAudio? = null,
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
    val artists = remember(song) { song?.ref<LArtist>() ?: emptyList() }
    val albums = remember(song) { song?.ref<LAlbum>() ?: emptyList() }
    val songsInfo = remember(song) {
        (song?.extraValue() ?: emptyMap()) + (song?.metadata?.toMap() ?: emptyMap())
            .filter { it.value.isNotBlank() }
    }

    val header = CoverHeader.register { key ->
        when (key) {
            CoverHeader.Param.SHARED_CONTEXT_SCOPE -> this@SharedContext
            CoverHeader.Param.COVER -> coverData
            CoverHeader.Param.TITLE -> song?.titleValue()
            CoverHeader.Param.SUBTITLE -> song?.subtitleValue()
            else -> null
        }
    }

    val metadata = MetadataInfos.register { key ->
        when (key) {
            MetadataInfos.Param.METADATA_MAP -> songsInfo
        }
    }

    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val smartBarHeight = PassThroughHelper.getValue(
        key = "SmartBarHeight",
        default = { navigationBar.calculateBottomPadding() }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .sharedBoundsV2("BOUND"),
        contentPadding = PaddingValues(bottom = smartBarHeight() + 16.dp),
    ) {
        header.invoke(this)

        items(
            items = albums,
            key = { it.idValue() }
        ) {
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
                album = it
            )
        }

        metadata.invoke(this)
    }
}


@Preview
@Composable
private fun SongDetailScreenContentPreview() = preview {
    enableNetworkImage()
    setFallbackUrl("https://www.dmoe.cc/random.php")

    SongDetailScreenContent(
        song = LAudio(
            id = "id",
            title = "ライアーメイデン (feat. りぃふ)",
            subtitle = "ヤバス/りぃふ",
            extra = mapOf(),
            metadata = Metadata(
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
    )
}

@Preview(device = Devices.TABLET)
@Composable
private fun SongDetailScreenContentPreviewForPad() {
    SongDetailScreenContentPreview()
}