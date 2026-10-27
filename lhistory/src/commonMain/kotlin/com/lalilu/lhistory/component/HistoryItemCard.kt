@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.lalilu.lhistory.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lalilu.lhistory.viewmodel.listeningTime
import com.lalilu.lmedia.rememberMediaCoverRequest
import kotlinx.datetime.*
import kotlin.time.Instant

@Composable
fun HistoryItemCard(
    modifier: Modifier = Modifier, title: String, artist: String, imageData: Any?,
    startTime: Long, duration: Long, enabled: Boolean = true,
    onLongClick: () -> Unit = {}, onClick: () -> Unit = {},
) {
    val time = Instant.fromEpochMilliseconds(startTime).toLocalDateTime(TimeZone.currentSystemDefault()).time
    Row(modifier.fillMaxWidth().alpha(if (enabled) 1f else .45f)
        .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(rememberMediaCoverRequest(imageData), null,
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(artist.takeIf { it.isNotBlank() }, "听了${listeningTime(duration)}").filterNotNull().joinToString(" · "),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
    }
}
