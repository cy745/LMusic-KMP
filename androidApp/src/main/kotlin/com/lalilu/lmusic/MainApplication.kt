package com.lalilu.lmusic

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.content.ContextCompat
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

    /**
     * 只在可调试构建里挂上 adb 调试通道（见 [DebugCommandReceiver]）。
     * 动态注册而不是写进 manifest：正式包里连这个 receiver 都不会存在，
     * 也就不需要给 release 留任何 exported 组件。
     */
    override fun onCreate() {
        super.onCreate()
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        ContextCompat.registerReceiver(
            this,
            DebugCommandReceiver(),
            IntentFilter(DebugCommandReceiver.ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        android.util.Log.i(
            "DebugCmd",
            "调试通道已注册：adb shell am broadcast -a ${DebugCommandReceiver.ACTION} --es cmd state",
        )
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
