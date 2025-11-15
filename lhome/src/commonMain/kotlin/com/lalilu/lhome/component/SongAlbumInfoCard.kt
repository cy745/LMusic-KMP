package com.lalilu.lhome.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview

@Composable
fun SongAlbumInfoCard(
    modifier: Modifier = Modifier,
    album: LAlbum,
) {
    SongAlbumInfoCard(
        modifier = modifier,
        imageData = album.items.firstOrNull() ?: album,
        title = album.title,
        subTitle = album.subtitle
    )
}

@Composable
fun SongAlbumInfoCard(
    modifier: Modifier = Modifier,
    imageData: Any,
    title: String,
    subTitle: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 20.dp,
        onClick = {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // TODO Animation BG

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    model = imageData,
                    contentScale = ContentScale.Crop,
                    contentDescription = "Recommend Card Cover Image"
                )
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    subTitle?.takeIf { it.isNotBlank() }?.let { artist ->
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun SongAlbumInfoCardPreview() = preview() {
    Column(modifier = Modifier) {
        repeat<PreviewPresets>(key = "SONGS", count = 10, shuffle = true) {
            SongAlbumInfoCard(
                modifier = Modifier,
                album = LAlbum(
                    id = "1",
                    title = stringValue("title"),
                    subtitle = stringValue("subtitle")
                )
            )
        }
    }
}