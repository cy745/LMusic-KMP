package com.lalilu.lmedia.remote

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.PlatformMediaSource
import org.koin.compose.koinInject

@Composable
fun RemoteServerPanel(
    modifier: Modifier = Modifier
) {
    val removeServer = koinInject<RemoteServer>()
    val sources = koinInject<PlatformMediaSource>()
    val config = removeServer.config

    Card {
        Column(
            modifier = modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.padding(vertical = 12.dp),
                text = "Remote Server: ${if (removeServer.running.value) "Running" else "Not Running"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "总开关",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = config.value.enable,
                    onCheckedChange = { config.value = config.value.copy(enable = it) }
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
