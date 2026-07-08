package com.lalilu.lmusic

import co.touchlab.kermit.Logger
import co.touchlab.kermit.chunked
import com.lalilu.krouter.InjectMap
import com.lalilu.krouter.KRouter
import com.lalilu.krouter.annotation.KInject
import com.lalilu.lmusic.util.DebugRecomposeLogger
import com.lalilu.lmusic.util.KermitKoinLogger
import com.lalilu.lmusic.util.MemoryLogWriter
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.observable.makeObservable
import com.skydoves.compose.stability.runtime.ComposeStabilityAnalyzer
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.core.scope.Scope
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.MediaSource
import org.koin.core.annotation.Module
import org.koin.core.annotation.ModuleProvider
import org.koin.dsl.module

fun KoinApplication.koinSetup() {
    KRouter.init(kRouterInjectMap()::getMap)
    Logger.addLogWriter(MemoryLogWriter.chunked())
    ComposeStabilityAnalyzer.setEnabled(true)
    ComposeStabilityAnalyzer.setLogger(DebugRecomposeLogger) // TODO 需要判断debug模式才开启

    logger(KermitKoinLogger(Logger.withTag("Koin")))

    val collectedModules = kRouterInjectMap().services
        .filterIsInstance<ModuleProvider>()
        .map { it.provide() }

    modules(collectedModules)
    modules(SharedModule)
}

private val SharedModule = module {
    @OptIn(ExperimentalSettingsApi::class)
    single<ObservableSettings> { Settings().makeObservable() }
    single<Settings> { get<ObservableSettings>() }
    single<Json> { Json { ignoreUnknownKeys = true } }

    // PlatformMediaSource — KSP doesn't process top-level @Single functions properly
    // in the current koin-annotations version (cy745 fork).
    single<PlatformMediaSource> {
        val sources: List<MediaSource> = getKoin().getAll()
        PlatformMediaSource(sources).apply { sources.forEach { it.init() } }
    }
}

@Module
@ComponentScan("com.lalilu.lmusic")
object AppModule

/**
 * kRouter注入，由ksp自动生成actual函数
 */
@KInject
expect fun kRouterInjectMap(): InjectMap