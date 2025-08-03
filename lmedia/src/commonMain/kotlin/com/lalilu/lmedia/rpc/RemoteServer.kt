package com.lalilu.lmedia.rpc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.PlatformMediaSource
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

typealias EngineFactory = ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>
typealias EngineServer = EmbeddedServer<ApplicationEngine, out ApplicationEngine.Configuration>

expect val serverEngineFactory: EngineFactory?

@Serializable
data class RemoteServerConfig(
    val enable: Boolean = false,
    val port: Int = 8087,
    val password: String = "",
    val selectedSourceKey: String = ""
) {
    companion object Companion {
        val Empty = RemoteServerConfig()
    }
}

@Suppress("UnusedFlow")
@OptIn(ExperimentalCoroutinesApi::class)
@Single(createdAtStart = true)
class RemoteServer(
    lMediaKV: LMediaKV,
    platformMediaSource: PlatformMediaSource
) : CoroutineScope {
    private val TAG = "RemoteServer"
    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            Logger.e(TAG, throwable)
        }

    /**
     * 筛选本机中可供外部远程访问的数据源
     */
    val remotableMediaSource by lazy { platformMediaSource.sources.filterIsInstance<RemotableMediaSource>() }

    /**
     * 服务器配置参数
     */
    val configItem = lMediaKV.obtain<RemoteServerConfig>(
        key = "REMOTE_SERVER_CONFIG",
        defaultValue = RemoteServerConfig.Empty
    )

    val configFlow = configItem.flow()

    /**
     * 服务器对象，使用Flow封装，当上游配置改变时，会重新创建服务器对象
     */
    val serverFlow = configFlow.flatMapLatest { config ->
        val targetMediaSource = remotableMediaSource
            .firstOrNull { it.requireName() == config.selectedSourceKey }

        if (!config.enable) {
            return@flatMapLatest flowOf(null)
        }

        if (config.port !in 1024..65535) {
            Logger.i(tag = TAG, messageString = "Invalid server config: port must be in range [1024, 65535]")
            return@flatMapLatest flowOf(null)
        }

        if (targetMediaSource == null) {
            Logger.i(tag = TAG, messageString = "Invalid server config: targetMediaSource not set")
            return@flatMapLatest flowOf(null)
        }

        callbackFlow<EngineServer?> {
            val server = provideRpcServer(port = config.port, mediaSource = targetMediaSource)
                ?.start(wait = false)

            if (server != null) {
                Logger.i(
                    tag = TAG,
                    messageString = "New Server instance created: ${server.hashCode()}"
                )
            }

            send(server)

            awaitClose {
                server?.stop()
                Logger.i(
                    tag = TAG,
                    messageString = "Server instance stopped: ${server?.hashCode()}"
                )
            }
        }
    }.stateIn(this, SharingStarted.Eagerly, null)
}

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
                    val name = remember { mutableStateOf("") }

                    LaunchedEffect(it) {
                        name.value = it.requireName()
                    }

                    FilterChip(
                        selected = config.selectedSourceKey == name.value,
                        onClick = {
                            removeServer.configItem.value = config.copy(selectedSourceKey = name.value)
                        },
                        label = { Text(text = name.value) }
                    )
                }
            }
        }
    }
}

private fun provideRpcServer(
    port: Int,
    mediaSource: RemotableMediaSource,
    config: Application.() -> Unit = {}
): EngineServer? {
    val factory = serverEngineFactory
    if (factory == null) return null

    return embeddedServer(factory, port) {
        install(Krpc)

        routing {
            rpc("/rpc") {
                rpcConfig { serialization { json() } }
                registerService<RemotableMediaSource> { mediaSource }
            }
            route("/") {
                get { call.respond("Hello world!") }
            }
        }

        config()
    }
}