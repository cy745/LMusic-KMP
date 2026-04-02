package com.lalilu.lhome.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview

@Composable
fun AudioItemCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    imageData: Any = Unit,
    isSelecting: () -> Boolean = { false },
    isSelected: () -> Boolean = { false },
    onEnterSelect: () -> Unit = {},
    onSelect: () -> Unit = {},
    onPlay: () -> Unit = {},
    onNavigateToDetail: () -> Unit = {}
) {
    val selectionColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    val bgColor by animateColorAsState(
        targetValue = if (isSelected()) selectionColor else Color.Transparent,
        label = "selection_bg"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(color = bgColor)
            .combinedClickable(
                onClick = {
                    if (isSelecting()) onSelect() else onPlay()
                },
                onLongClick = {
                    if (isSelecting()) onEnterSelect() else onNavigateToDetail()
                }
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                modifier = Modifier.alpha(0.6f),
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        AsyncImage(
            modifier = Modifier
                .size(64.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1f.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.2f),
                    shape = RoundedCornerShape(8.dp)
                )
                .background(MaterialTheme.colorScheme.onBackground.copy(0.15f))
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onEnterSelect() }
                ),
            model = imageData,
            contentScale = ContentScale.Crop,
            contentDescription = "Cover for $title"
        )
    }
}

@Preview
@Composable
private fun AudioItemCardPreview() = preview {
    Column(modifier = Modifier) {
        repeat<PreviewPresets>(key = "SONGS", count = 10, shuffle = true) {
            AudioItemCard(
                modifier = Modifier.fillMaxWidth()
                    .padding(8.dp),
                title = stringValue("title"),
                subtitle = stringValue("subtitle"),
                imageData = intValue("imageData")
            )
        }
    }
}