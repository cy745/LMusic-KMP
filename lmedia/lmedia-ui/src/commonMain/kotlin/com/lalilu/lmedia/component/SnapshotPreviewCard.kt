package com.lalilu.lmedia.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.lmedia.entity.Snapshot
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Composable
fun SnapshotPreviewCard(
    modifier: Modifier = Modifier,
    snapshot: () -> Snapshot
) {
    val value = snapshot()

    Column(modifier = modifier) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(0.5f)
                )
            ) {
                Text(
                    modifier = Modifier.padding(12.dp),
                    text = "Songs: ${value.audios.size}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Card(
                modifier = Modifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(0.5f)
                )
            ) {
                Text(
                    modifier = Modifier.padding(12.dp),
                    text = "Albums: ${value.albums.size}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Card(
                modifier = Modifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(0.5f)
                )
            ) {
                Text(
                    modifier = Modifier.padding(12.dp),
                    text = "Artists: ${value.artists.size}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Card(
                modifier = Modifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(0.5f)
                )
            ) {
                Text(
                    modifier = Modifier.padding(12.dp),
                    text = "Genres: ${value.genres.size}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        val timeAgo = remember(value) {
            val updateTime = value.updateTime
            val instant = Instant.fromEpochMilliseconds(updateTime)
            if (Clock.System.now().toEpochMilliseconds() - updateTime > 3600 * 1000) {
                return@remember "最近更新：" + instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            }
            "最近更新：${HumanReadable.timeAgo(instant)}"
        }

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .alpha(0.3f),
            text = timeAgo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}