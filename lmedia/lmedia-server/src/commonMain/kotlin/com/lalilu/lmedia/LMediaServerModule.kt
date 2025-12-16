package com.lalilu.lmedia

import com.lalilu.common.ext.KModule
import com.lalilu.common.ext.KoinModule
import dev.whyoleg.sweetspi.ServiceProvider
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.ksp.generated.module

@Module
@ServiceProvider
@ComponentScan("com.lalilu.lmedia")
object LMediaServerModule : KModule {
    override fun get(): KoinModule = this.module
}

typealias EngineFactory = ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>
typealias EngineServer = EmbeddedServer<ApplicationEngine, out ApplicationEngine.Configuration>

val SERVER_ENGINE_FACTORY: EngineFactory = CIO