package com.lalilu.lmedia.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.component.BaseSourceCard
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

    BaseSourceCard(
        modifier = modifier,
        title = "Remote Server",
        subtitle = if (removeServer.running.value) "Running at port: ${config.value.port}" else "Not Running",
        actionContent = {
            Switch(
                checked = config.value.enable,
                onCheckedChange = { config.value = config.value.copy(enable = it) }
            )
        },
        content = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.value.password,
                onValueChange = { config.value = config.value.copy(password = it) },
                label = { Text("密码") },
                placeholder = { Text("留空表示无需密码") },
            )
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
    )
}
