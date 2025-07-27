package com.lalilu.lmusic

import com.lalilu.krouter.KRouter
import com.lalilu.krouter.generated.KRouterInjectMap
import com.lalilu.lmedia.LMediaModule
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.observable.makeObservable
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.ksp.generated.module

fun KoinApplication.koinSetup() {
    KRouter.init(KRouterInjectMap::getMap)
    modules(SharedModule)
    modules(LMediaModule.module)
}

private val SharedModule = module {
    @OptIn(ExperimentalSettingsApi::class)
    single<ObservableSettings> { Settings().makeObservable() }
    single<Settings> { get<ObservableSettings>() }
}