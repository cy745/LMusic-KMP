package com.lalilu.lmedia.rpc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.rpc.impl.RemoteMediaSourceServiceImpl
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

expect val serverEngineFactory: ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>?

fun provideRpcServer(
    config: Application.() -> Unit = {}
): EmbeddedServer<ApplicationEngine, out ApplicationEngine.Configuration>? {
    val factory = serverEngineFactory
    if (factory == null) return null

    return embeddedServer(factory, 7963) {
        install(Krpc)

        routing {
            rpc("/rpc") {
                rpcConfig { serialization { json() } }
                registerService<RemoteMediaSourceService> { RemoteMediaSourceServiceImpl }
            }
        }

        config()
    }
}

@Serializable
data class RemoteServerConfig(
    val enable: Boolean = false,
    val port: Int = 8087,
    val password: String = ""
) {
    companion object Companion {
        val Empty = RemoteServerConfig()
    }
}

@Single(createdAtStart = true)
class RemoteServer(
    lMediaKV: LMediaKV,
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    private val serverReady = mutableStateOf(false)
    private val server = provideRpcServer {
        monitor.subscribe(ServerReady) { environment ->
            serverReady.value = true
        }
        monitor.subscribe(ApplicationStopped) { event ->
            serverReady.value = false
        }
    }

    init {
        server?.start(false)
    }
}

@Composable
fun RemoteServerPanel(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column {
            Row {
                Text("Server")
            }
        }
    }
}