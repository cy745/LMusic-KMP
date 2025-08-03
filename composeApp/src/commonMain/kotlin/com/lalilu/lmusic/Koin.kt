package com.lalilu.lmusic

import com.lalilu.krouter.KRouter
import com.lalilu.krouter.generated.KRouterInjectMap
import com.lalilu.lhome.LHomeModule
import com.lalilu.lmedia.LMediaModule
import com.lalilu.lplayer.LPlayerModule
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.observable.makeObservable
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.dsl.module
import org.koin.ksp.generated.module

fun KoinApplication.koinSetup() {
    KRouter.init(KRouterInjectMap::getMap)
    modules(SharedModule)
    modules(AppModule.module)
    modules(LMediaModule.module)
    modules(LHomeModule.module)
    modules(LPlayerModule.module)
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