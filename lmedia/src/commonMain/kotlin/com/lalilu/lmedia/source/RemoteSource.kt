package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.rpc.RemoteMediaSourceService
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single

@Serializable
data class RemoteSourceConfig(
    val enable: Boolean = false,
    val url: String = "",
    val password: String = ""
) {
    companion object {
        val Empty = RemoteSourceConfig()
    }
}

@Suppress("UnusedFlow")
@OptIn(ExperimentalCoroutinesApi::class)
@Single
class RemoteSource(
    lMediaKV: LMediaKV,
) : MediaSource {
    override val name: String = "RemoteSource"
    private val configItem = lMediaKV.obtain<RemoteSourceConfig>(
        key = "REMOTE_CONFIG",
        defaultValue = RemoteSourceConfig.Empty
    )
    private val configFlow = configItem.flow()
    private val rpcClientFlow = configFlow.flatMapLatest { config ->
        if (!config.enable || config.url.isBlank()) {
            return@flatMapLatest flowOf(null)
        }

        val client = runCatching {
            HttpClient {
                installKrpc {
                    serialization {
                        json()
                    }
                }
            }.rpc {
                url("ws://${config.url}/rpc")
            }
        }.getOrNull()

        flowOf(client)
            .onCompletion { client?.close("重启Client") }
    }

    private val rpcServiceFlow = rpcClientFlow.mapLatest { client ->
        client?.withService<RemoteMediaSourceService>()
    }

    override fun source(): Flow<Snapshot> {
        return rpcServiceFlow.flatMapLatest {
            it?.source() ?: flowOf(Snapshot.Empty)
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        Card(modifier = modifier) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val config by configFlow.collectAsState(RemoteSourceConfig.Empty)
                val enable = remember(config) { mutableStateOf(config.enable) }
                val url = remember(config) { mutableStateOf(config.url) }
                val password = remember(config) { mutableStateOf(config.password) }
                val edited = remember {
                    derivedStateOf {
                        enable.value != config.enable ||
                                url.value != config.url ||
                                password.value != config.password
                    }
                }

                Text(text = name)

                Row {
                    Text("Enable")
                    Switch(
                        enabled = true,
                        checked = enable.value,
                        onCheckedChange = { enable.value = it }
                    )
                }

                TextField(
                    value = url.value,
                    onValueChange = { url.value = it },
                    label = { Text(text = "URL") }
                )
                TextField(
                    value = password.value,
                    onValueChange = { password.value = it },
                    label = { Text(text = "Password") }
                )

                Button(
                    enabled = edited.value,
                    onClick = {
                        configItem.value = RemoteSourceConfig(
                            enable = enable.value,
                            url = url.value,
                            password = password.value
                        )
                    }
                ) {
                    Text(text = "Update Config")
                }
            }
        }
    }
}