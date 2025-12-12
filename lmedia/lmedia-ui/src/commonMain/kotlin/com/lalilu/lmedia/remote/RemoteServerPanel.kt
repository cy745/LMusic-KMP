package com.lalilu.lmedia.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.server.RemoteServer

@Composable
fun RemoteServerPanel(modifier: Modifier = Modifier) {
    val removeServer = koinInject<RemoteServer>()
    val config by removeServer.configFlow.collectAsState(RemoteServerConfig.Empty)
    val serverItem = removeServer.serverFlow.collectAsState()

    Card {
        Column(
            modifier = modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.padding(vertical = 12.dp),
                text = "Remote Server: ${if (serverItem.value == null) "Not Running" else "Running"}",
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
                    checked = config.enable,
                    onCheckedChange = {
                        removeServer.configItem.value = config.copy(enable = it)
                    }
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                removeServer.remotableMediaSource.forEach {
                    FilterChip(
                        selected = config.selectedSourceKey == it.name,
                        onClick = {
                            removeServer.configItem.value = config.copy(selectedSourceKey = it.name)
                        },
                        label = { Text(text = it.name) }
                    )
                }
            }
        }
    }
}
