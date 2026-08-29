package com.lalilu.lmusic

import co.touchlab.kermit.Logger
import co.touchlab.kermit.chunked
import com.lalilu.krouter.InjectMap
import com.lalilu.krouter.KRouter
import com.lalilu.krouter.annotation.KInject
import com.lalilu.lmusic.util.DebugRecomposeLogger
import com.lalilu.lmusic.util.KermitKoinLogger
import com.lalilu.lmusic.util.MemoryLogWriter
import com.lalilu.lplayer.playback.HistoryStorage
import com.lalilu.lplayer.playback.HistoryStorageImpl
import com.lalilu.lplayer.playback.PlaybackHistory
import com.lalilu.lplayer.playback.PlaybackHistoryImpl
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.observable.makeObservable
import com.skydoves.compose.stability.runtime.ComposeStabilityAnalyzer
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.ModuleProvider
import org.koin.dsl.module
import org.koin.core.annotation.Module as KoinModule

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
    // coerceInputValues / explicitNulls = false：容忍远端返回的 null 字段
    // （部分 Subsonic 服务器对缺省字段返回 null，而不是省略 key），
    // 避免整次同步因单个字段形态差异反序列化失败。
    single<Json> {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
    }

    single<PlaybackHistory> { PlaybackHistoryImpl(historyStorage = get()) }
    single<HistoryStorage> { HistoryStorageImpl() }
}

@KoinModule
@ComponentScan("com.lalilu.lmusic")
object AppModule

/**
 * kRouter注入，由ksp自动生成actual函数
 */

@KInject
expect fun kRouterInjectMap(): InjectMap