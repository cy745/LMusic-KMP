package com.lalilu.lplayer

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lalilu.extensions.SharedContext
import com.lalilu.extensions.SharedMap
import com.lalilu.extensions.buildSharedMap
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview


@Composable
fun SongCard(
    modifier: Modifier = Modifier,
    id: String,
    imageData: Any? = null,
    title: String = "",
    subtitle: String = "",
    extraText: String = "",
    onClick: () -> Unit = {},
    onLongClick: ((SharedMap) -> Unit)? = null,
) = SharedContext(
    sharedMap = buildSharedMap(
        id = id,
        keys = listOf(
            "BOUND",
            "COVER",
            "TITLE",
            "SUBTITLE"
        )
    )
) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke(sharedMap) }
            )
            .padding(16.dp)
            .sharedBoundsV2("BOUND"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(5.dp)
        ) {
            AsyncImage(
                modifier = Modifier
                    .size(64.dp)
                    .aspectRatio(1f)
                    .sharedElementV2("COVER")
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onLongClick = {
                            onLongClick?.invoke(sharedMap)
                        },
                        onClick = onClick
                    ),
                model = imageData,
                contentScale = ContentScale.Crop,
                contentDescription = "Song Card Image"
            )
        }

        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                modifier = Modifier.sharedBoundsV2("TITLE"),
                text = title.ifBlank { "Unknown Title" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier
                        .sharedElementV2("SUBTITLE")
                        .alpha(0.6f)
                        .weight(1f),
                    text = subtitle.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (extraText.isNotBlank()) {
                    Text(
                        modifier = Modifier
                            .alpha(0.6f),
                        text = extraText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SongCardPreview() = preview {
    Column {
        repeat<PreviewPresets>(
            count = 10,
            key = "SONGS",
            shuffle = true
        ) {
            SongCard(
                id = stringValue("title"),
                title = stringValue("title"),
                subtitle = stringValue("subtitle")
            )
        }
    }
}