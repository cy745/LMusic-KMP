package com.lalilu.lplayer

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.lalilu.preview.PreviewPresets
import com.lalilu.preview.preview
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun SongCard(
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String = "",
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title.ifBlank { "Unknown Title" },
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = subtitle.ifBlank { "Unknown Artist" },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(0.6f)
        )
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