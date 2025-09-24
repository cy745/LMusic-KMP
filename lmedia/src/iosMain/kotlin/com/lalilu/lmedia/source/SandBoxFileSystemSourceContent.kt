package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.lmedia.generated.resources.Res
import com.lalilu.lmedia.rpc.EngineServer
import com.lalilu.lmedia.rpc.serverEngineFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private var server: EmbeddedServer<*, *>? by mutableStateOf(null)

@Composable
fun SandBoxFileSystemSourceContent(
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        delay(2000)
        if (isActive) {
            runCatching {
                val temp = provideSandBoxFileSystemServer(8096)
                temp?.startSuspend(wait = false)
                server = temp
            }.getOrElse {
                Logger.i(
                    tag = "SandBoxFileSystemSourceContent",
                    messageString = "Error starting server",
                    throwable = it
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                server?.stop(1000, 1000)
                server = null
            }.getOrElse {
                Logger.i(
                    tag = "SandBoxFileSystemSourceContent",
                    messageString = "Error stopping server",
                    throwable = it
                )
            }
        }
    }

    Card(modifier = modifier) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(text = "SandBoxFileSystemSource, server status: isRunning: ${server != null}")
        }
    }
}


private fun provideSandBoxFileSystemServer(
    port: Int,
    config: Application.() -> Unit = {}
): EngineServer? {
    val factory = serverEngineFactory ?: return null

    return embeddedServer(factory, port) {
        install(CORS) {
            anyHost()
            anyMethod()
        }

        routing {
            get("/") {
                call.respondBytes(
                    contentType = ContentType.Text.Html,
                    bytes = Res.readBytes("files/index.html")
                )
            }
        }

        config()
    }
}