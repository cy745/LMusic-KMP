package com.lalilu.lmedia.rpc

import io.ktor.server.cio.*
import io.ktor.server.engine.*

actual val serverEngineFactory: ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>?
    get() = CIO