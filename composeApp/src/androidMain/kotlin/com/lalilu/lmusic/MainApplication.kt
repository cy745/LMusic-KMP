package com.lalilu.lmusic

import android.app.Application
import android.os.Build
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.russhwolf.settings.SettingsInitializer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.manualFileKitCoreInitialization
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.KoinConfiguration

// this part should be configured only once in the app to use native android logging
object Static {
    init {
        System.setProperty("kotlin-logging-to-android-native", "true")
    }
}

private val static = Static

// this should be configured in every class that uses logging
private val logger = KotlinLogging.logger {}
@OptIn(KoinExperimentalAPI::class)
class MainApplication : Application(), KoinStartup {

    override fun onKoinStartup(): KoinConfiguration = KoinConfiguration {
        // 传入context到settings
        SettingsInitializer().create(this@MainApplication)
        FileKit.manualFileKitCoreInitialization(this@MainApplication)

        androidContext(this@MainApplication)
        koinSetup()

        logger.info { "This is logging of - kotlin-logging" }

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