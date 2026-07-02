package com.lalilu.lmedia.server

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan("com.lalilu.lmedia")
object LMediaServerModule

typealias EngineFactory = ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>
typealias EngineServer = EmbeddedServer<ApplicationEngine, out ApplicationEngine.Configuration>

val SERVER_ENGINE_FACTORY: EngineFactory = CIO