package com.lalilu.lhome.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lalilu.component.WrapBox
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview

@Composable
fun SongAlbumInfoCard(
    modifier: Modifier = Modifier,
    album: LAlbum,
    onClick: () -> Unit = {}
) {
    val imageData = remember(album) { album.ref<LAudio>().firstOrNull() ?: album }

    SongAlbumInfoCard(
        modifier = modifier,
        imageData = imageData,
        title = album.titleValue(),
        subTitle = album.subtitleValue(),
        onClick = onClick
    )
}

@Composable
fun SongAlbumInfoCard(
    modifier: Modifier = Modifier,
    imageData: Any,
    title: String,
    subTitle: String? = null,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(0.1f)
        ),
        tonalElevation = 2.dp,
        onClick = onClick
    ) {
        WrapBox(
            background = {
                AsyncImage(
                    modifier = Modifier.fillMaxSize()
                        .alpha(0.1f)
                        .blur(25.dp),
                    model = imageData,
                    contentScale = ContentScale.Crop,
                    contentDescription = "Recommend Card Cover Image"
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    modifier = Modifier.weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    subTitle?.takeIf { it.isNotBlank() }?.let { artist ->
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
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
    enableNetworkImage()
    setFallbackUrl("https://www.dmoe.cc/random.php")

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat<PreviewPresets>(key = "SONGS", count = 10, shuffle = true) {
            val album = LAlbum(
                id = "1",
                title = stringValue("title"),
                subtitle = stringValue("subtitle")
            )

            SongAlbumInfoCard(
                modifier = Modifier,
                album = album
            )
        }
    }
}