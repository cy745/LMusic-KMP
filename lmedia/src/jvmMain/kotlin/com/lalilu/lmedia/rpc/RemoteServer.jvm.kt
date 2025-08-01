package com.lalilu.lmedia.rpc

import io.ktor.server.engine.*
import io.ktor.server.netty.*

actual val serverEngineFactory: ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>?
    get() = Netty