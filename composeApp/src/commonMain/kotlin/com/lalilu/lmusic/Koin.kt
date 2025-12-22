package com.lalilu.lmusic

import co.touchlab.kermit.Logger
import co.touchlab.kermit.chunked
import com.lalilu.common.ext.KModule
import com.lalilu.krouter.KRouter
import com.lalilu.krouter.generated.KRouterInjectMap
import com.lalilu.lhome.LHomeModule
import com.lalilu.lmedia.LMediaModule
import com.lalilu.lmusic.util.DebugRecomposeLogger
import com.lalilu.lmusic.util.KermitKoinLogger
import com.lalilu.lmusic.util.MemoryLogWriter
import com.lalilu.lplayer.LPlayerModule
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.observable.makeObservable
import com.skydoves.compose.stability.runtime.ComposeStabilityAnalyzer
import dev.whyoleg.sweetspi.ServiceLoader
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module
import org.koin.ksp.generated.module

fun KoinApplication.koinSetup() {
    KRouter.init(KRouterInjectMap::getMap)
    Logger.addLogWriter(MemoryLogWriter.chunked())
    ComposeStabilityAnalyzer.setEnabled(true)
    ComposeStabilityAnalyzer.setLogger(DebugRecomposeLogger) // TODO 需要判断debug模式才开启

    logger(KermitKoinLogger(Logger.withTag("Koin")))
    modules(SharedModule)
    modules(AppModule.module)
    modules(LHomeModule.module)
    modules(LPlayerModule.module)
    modules(
        ServiceLoader.load(KModule::class)
            .map(KModule::get)
    )
}

private val SharedModule = module {
    @OptIn(ExperimentalSettingsApi::class)
    single<ObservableSettings> { Settings().makeObservable() }
    single<Settings> { get<ObservableSettings>() }
    single<Json> { Json { ignoreUnknownKeys = true } }
}

@Module
@ComponentScan("com.lalilu.lmusic")
object AppModule