package com.lalilu.lmedia.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${snapshot.audios.size} 首歌曲",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "最近一次成功结果",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = timeText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
