package com.lalilu.lmusic

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.russhwolf.settings.SettingsInitializer
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.manualFileKitCoreInitialization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.KoinConfiguration

@OptIn(KoinExperimentalAPI::class)
class MainApplication : Application(), KoinStartup {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        OfflineSentryReporter.install(this)
    }

    override fun onKoinStartup(): KoinConfiguration = KoinConfiguration {
        // 传入context到settings
        SettingsInitializer().create(this@MainApplication)
        FileKit.manualFileKitCoreInitialization(this@MainApplication)

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

        // 把 Coil ImageLoader 预热放到 IO 线程上：第一次 SingletonImageLoader.get(context)
        // 会同步构造 ImageLoader + 加载 fetcher factories + 初始化 Bitmap pool，
        // 不应阻塞 KoinStartup 主线程。
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                SingletonImageLoader.get(this@MainApplication)
            } catch (_: Throwable) {
            }
        }
    }
}
