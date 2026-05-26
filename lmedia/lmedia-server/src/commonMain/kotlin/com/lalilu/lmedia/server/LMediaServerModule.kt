package com.lalilu.lmedia.server

import com.lalilu.common.ext.KModule
import com.lalilu.common.ext.KoinModule
import com.lalilu.krouter.annotation.KService
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.ksp.generated.module

@Module
@KService
@ComponentScan("com.lalilu.lmedia")
object LMediaServerModule : KModule {
    override fun get(): KoinModule = this.module
}

typealias EngineFactory = ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>
typealias EngineServer = EmbeddedServer<ApplicationEngine, out ApplicationEngine.Configuration>

val SERVER_ENGINE_FACTORY: EngineFactory = CIO