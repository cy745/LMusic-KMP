package com.lalilu.lmedia.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory

actual val serverEngineFactory: ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>?
    get() = CIO