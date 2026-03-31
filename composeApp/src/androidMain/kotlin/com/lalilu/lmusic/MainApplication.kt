package com.lalilu.lmusic

import android.app.Application
import android.os.Build
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.russhwolf.settings.SettingsInitializer
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.KoinConfiguration

@OptIn(KoinExperimentalAPI::class)
class MainApplication : Application(), KoinStartup {

    override fun onKoinStartup(): KoinConfiguration = KoinConfiguration {
        // 传入context到settings
        SettingsInitializer().create(this@MainApplication)

        androidContext(this@MainApplication)
        koinSetup()

        platformSetupCoil(
            components = {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
        )
    }
}