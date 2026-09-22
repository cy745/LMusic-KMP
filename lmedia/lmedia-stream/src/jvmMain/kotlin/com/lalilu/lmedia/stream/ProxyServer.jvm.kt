package com.lalilu.lmedia.stream

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/** Desktop/JVM：用 Netty，避开 CIO 在写阻塞时的 select 忙等（见 `ProxyServer.kt` 与 issue #30）。 */
internal actual fun createProxyServer(
    host: String,
    port: Int,
    module: Application.() -> Unit,
): EmbeddedServer<*, *> = embeddedServer(Netty, port = port, host = host) { module() }
