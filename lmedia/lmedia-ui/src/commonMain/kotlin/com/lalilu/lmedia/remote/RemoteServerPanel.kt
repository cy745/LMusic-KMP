package com.lalilu.lmedia.remote

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.PlatformMediaSource
import org.koin.compose.koinInject
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
@Composable
fun RemoteServerPanel(
    modifier: Modifier = Modifier,
) {
    val removeServer = koinInject<RemoteServer>()
    val sources = koinInject<PlatformMediaSource>()
    val config = removeServer.config

    Card(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remote Server",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        modifier = Modifier
                            .alpha(0.6f)
                            .padding(top = 8.dp),
                        text = if (removeServer.running.value) "Running at port: ${config.value.port}"
                        else "Not Running",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Switch(
                    checked = config.value.enable,
                    onCheckedChange = { config.value = config.value.copy(enable = it) }
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.sources.forEach {
                    FilterChip(
                        selected = config.value.sourceName == it.name,
                        onClick = { config.value = config.value.copy(sourceName = it.name) },
                        label = { Text(text = it.name) }
                    )
                }
            }
        }
    }
}
