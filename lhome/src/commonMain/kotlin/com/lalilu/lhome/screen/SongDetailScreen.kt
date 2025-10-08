package com.lalilu.lhome.screen

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.lalilu.extensions.SharedContext
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.navigation.Screen
import com.lalilu.preview.preview
import org.jetbrains.compose.ui.tooling.preview.Preview


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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = navigationBar
    ) {
        item {
            AsyncImage(
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(1f)
                    .sharedElementV2("COVER"),
                model = song,
                contentDescription = null,
            )
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(top = 8.dp)
                        .sharedElementV2("TITLE"),
                    text = song?.title ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W600,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onBackground,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    modifier = Modifier.alpha(0.6f)
                        .sharedElementV2("SUBTITLE"),
                    text = song?.subtitle ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onBackground,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


@Preview
@Composable
private fun SongDetailScreenContentPreview() = preview {
    SongDetailScreenContent()
}

