package com.lalilu.lhome.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.lalilu.extensions.SharedContext
import com.lalilu.extensions.clipFade
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhome.component.SongAlbumInfoCard
import com.lalilu.lhome.component.SongInformationCard
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.navigation.Screen
import com.lalilu.preview.preview


@Destination("/song/detail")
data class SongDetailScreen(
    val mediaId: String,
    val coverCacheKey: String? = null,
    val sharedMap: Map<String, String> = emptyMap(),
) : Screen {
    override val key: String = "${super.key}_$mediaId"

    @Composable
    override fun Content() {
        val song by remember { LMedia.instance.getFlow<LAudio>(id = mediaId) }
            .collectAsState(LMedia.instance.get<LAudio>(id = mediaId))

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
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val context = LocalPlatformContext.current
    val coverData = remember(song) {
        ImageRequest.Builder(context)
            .placeholderMemoryCacheKey(coverCacheKey)
            .data(song)
            .build()
    }
    val artists = remember(song) { song?.ref<LArtist>() ?: emptyList() }
    val albums = remember(song) { song?.ref<LAlbum>() ?: emptyList() }
    val songsInfo = remember(song) { (song?.extra ?: emptyMap()) + (song?.metadata?.toMap() ?: emptyMap()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .sharedBoundsV2("BOUND"),
        contentPadding = navigationBar
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxWidth()
                        .aspectRatio(1f)
                        .sharedElementV2("COVER")
                        .clipFade(
                            lengthDp = 300.dp,
                            alignmentY = Alignment.Bottom
                        ),
                    model = coverData,
                    contentDescription = null,
                )

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp)
                            .sharedElementV2(
                                key = "TITLE",
                            ),
                        text = song?.title ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.W600,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onBackground,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        modifier = Modifier.sharedElementV2("SUBTITLE")
                            .alpha(0.6f),
                        text = song?.subtitle ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onBackground,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        items(
            items = albums,
            key = { it.id }
        ) {
            SongAlbumInfoCard(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp),
                album = it
            )
        }

        item {
            SongInformationCard(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp),
                extra = songsInfo
            )
        }
    }
}


@Preview
@Composable
private fun SongDetailScreenContentPreview() = preview {
    SongDetailScreenContent()
}

