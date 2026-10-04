package com.lalilu.lmedia.stream

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer

/** Android / iOS / Web：保持 CIO，跟改动前一致（Netty 只用在桌面端，见 `ProxyServer.kt`）。 */
internal actual fun createProxyServer(
    host: String,
    port: Int,
    module: Application.() -> Unit,
): EmbeddedServer<*, *> = embeddedServer(CIO, port = port, host = host) { module() }
