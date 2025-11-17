package com.lalilu.lmusic

import android.app.Application
import android.os.Build
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            module {
                androidContext(this@MainApplication)
            }
            koinSetup()
        }

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