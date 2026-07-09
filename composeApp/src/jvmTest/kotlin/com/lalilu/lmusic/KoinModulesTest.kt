package com.lalilu.lmusic

import com.lalilu.common.kv.KVSaver
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.observable.makeObservable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.ModuleProvider
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.check.checkModules
import kotlin.test.AfterTest
import kotlin.test.Test

class KoinModulesTest {

    private val sharedModule = module {
        single<ObservableSettings> { Settings().makeObservable() }
        single<Settings> { get<ObservableSettings>() }
        single<Json> { Json { ignoreUnknownKeys = true } }
        single<KVSaver> { object : KVSaver {
            @Suppress("UNCHECKED_CAST")
            override fun <T> readData(key: String, defaultValue: T?, clazz: kotlin.reflect.KClass<*>): T = defaultValue as T
            override fun <T> saveData(key: String, value: T?, clazz: kotlin.reflect.KClass<*>) {}
        } }
        single<com.lalilu.lmedia.source.Saver> { com.lalilu.lmedia.source.Saver.Empty }
    }

    /** Providers needing Android Context / Koin Scope / platform APIs.
     *  These can only be fully verified in an Android instrumented test
     *  (androidApp/src/androidTest/). On JVM they fail because:
     *  - AppModule → needs androidContext (KoinStartup)
     *  - LPlayerModule, LMediaModule → PlatformMediaSource needs Koin Scope
     *  - LMediaDataModule → LMedia needs PlatformMediaSource
     *  - LMediaUiModule → RemoteServer needs PlatformMediaSource
     *  - LHistoryModule → may need Android-specific deps
     *  Feature modules (LHome, LAlbum...) depend on data interfaces from
     *  LMediaDataModule, so they pass only when tested together on-device.
     */
    private val platformOrDataProviders = setOf(
        "AppModuleProvider",
        "LPlayerModuleProvider",
        "LMediaModuleProvider",
        "LMediaDataModuleProvider",
        "LMediaUiModuleProvider",
        "LHistoryModuleProvider",
        "LHomeModuleProvider",
        "LAlbumModuleProvider",
        "LArtistModuleProvider",
        "LPlaylistModuleProvider",
        "LMediaDomainModuleProvider",
    )

    @AfterTest
    fun cleanup() = stopKoin()

    @Test
    fun `kRouterInjectMap contains LMediaDomainModule`() {
        val providers = kRouterInjectMap().services
            .filterIsInstance<ModuleProvider>()
            .map { it.javaClass.simpleName }

        assert(providers.any { it == "LMediaDomainModuleProvider" }) {
            "LMediaDomainModuleProvider not found! Found: $providers"
        }
    }

    @Test
    fun `all non-platform modules together`() {
        val modules = kRouterInjectMap().services
            .filterIsInstance<ModuleProvider>()
            .filterNot { it.javaClass.simpleName in platformOrDataProviders }
            .map { it.provide() }

        startKoin { modules(modules + listOf(sharedModule)) }
            .checkModules()
    }

    @Test
    fun `LSettingsModule standalone`() = testModule("LSettingsModuleProvider")
    @Test
    fun `LLyricViewModule standalone`() = testModule("LLyricViewModuleProvider")

    private fun testModule(name: String) {
        val mod = provide(name)
        startKoin { modules(listOf(mod) + listOf(sharedModule)) }
            .checkModules()
    }

    private fun provide(name: String) =
        kRouterInjectMap().services
            .filterIsInstance<ModuleProvider>()
            .first { it.javaClass.simpleName == name }
            .provide()
}
