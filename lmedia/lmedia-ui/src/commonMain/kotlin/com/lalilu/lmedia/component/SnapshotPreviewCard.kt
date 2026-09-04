package com.lalilu.lmedia.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** 面向用户的紧凑结果摘要；revision 等内部字段不再占用卡片的主要视觉层级。 */
@OptIn(ExperimentalTime::class)
@Composable
fun SnapshotPreviewCard(
    snapshot: Snapshot,
    modifier: Modifier = Modifier,
) {
    val timeText = remember(snapshot.updateTime) {
        val updateTime = snapshot.updateTime
        val instant = Instant.fromEpochMilliseconds(updateTime)
        if (Clock.System.now().toEpochMilliseconds() - updateTime > 3600 * 1000) {
            instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        } else {
            HumanReadable.timeAgo(instant)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "最近同步",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
        )
        Text(
            text = "${snapshot.audios.size} 首 · $timeText",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
