package com.lalilu.lmusic

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            module {
                androidContext(this@MainApplication)
                androidLogger(Level.DEBUG)
            }
            koinSetup()
        }
    }
}