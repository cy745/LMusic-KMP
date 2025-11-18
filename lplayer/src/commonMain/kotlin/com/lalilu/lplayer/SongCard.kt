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
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview


@Composable
fun SongCard(
    modifier: Modifier = Modifier,
    imageData: Any? = null,
    title: String = "",
    subtitle: String = "",
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
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
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onLongClick = {
                            onLongClick?.invoke()
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
                text = title.ifBlank { "Unknown Title" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                modifier = Modifier.alpha(0.6f),
                text = subtitle.ifBlank { "Unknown Artist" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
                title = stringValue("title"),
                subtitle = stringValue("subtitle")
            )
        }
    }
}