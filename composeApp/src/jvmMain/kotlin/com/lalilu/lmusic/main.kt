package com.lalilu.lmusic

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lalilu.lmedia.domain.debug.DebugSwitches
import com.lalilu.lmusic.window.WindowFrame
import io.github.vinceglb.filekit.FileKit
import org.jetbrains.skiko.hostOs
import org.koin.core.context.startKoin

@OptIn(InternalComposeUiApi::class)
fun main(args: Array<String>) {
    val crashReportRequest = DesktopCrashReporter.parseRequest(args)
    if (crashReportRequest != null) {
        DesktopCrashReporter.show(crashReportRequest)
        return
    }

    DesktopCrashReportStore.latestReportId(onlyUnviewed = true)?.let { reportId ->
        DesktopCrashReporter.show(DesktopCrashReportRequest(reportId))
        return
    }
    DesktopOfflineSentryReporter.install()

    // 桌面端没有 adb 通道，调试开关走环境变量：`LMUSIC_DEBUG=1 ./gradlew :composeApp:run`。
    // 打开后 LAudioFetcher / 播放时长会打 DebugCover / DebugDuration，行为和 Android 调试通道一致，
    // 用来在桌面复现"切到一首还没加载过的网络歌"这类场景（清缓存可以直接删
    // `%LOCALAPPDATA%\LMusic\Cache\lmedia\webdav\{meta,covers}\<key>.*`）。
    if (System.getenv("LMUSIC_DEBUG") == "1") {
        DebugSwitches.enabled = true
    }

    FileKit.init(appId = "LMusic")
    startKoin { koinSetup() }
    platformSetupCoil()

    if (hostOs.isMacOS) {
        System.setProperty("apple.awt.application.appearance", "system")
    }

    application {
        val windowState = WindowStateKeeper.rememberWindowState()
        val closeFunc = remember {
            {
                WindowStateKeeper.saveWindowState(windowState)
                exitApplication()
            }
        }

        Window(
            onCloseRequest = closeFunc,
            state = windowState,
            title = "LMusic",
        ) {
            window.background = if (isSystemInDarkTheme()) java.awt.Color.BLACK else java.awt.Color.WHITE

            WindowFrame(
                state = windowState,
                onCloseRequest = closeFunc
            ) { windowInset: WindowInsets, captionBarInset: WindowInsets ->
                val insets = LocalPlatformWindowInsets.current
                val density = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current

                val newInsets = remember(insets, windowInset) {
                    object : PlatformWindowInsets by insets {
                        override val statusBars: PlatformInsets = PlatformInsets(
                            left = windowInset.getLeft(density, layoutDirection),
                            top = windowInset.getTop(density),
                            right = windowInset.getRight(density, layoutDirection),
                            bottom = windowInset.getBottom(density)
                        )
                    }
                }

                CompositionLocalProvider(LocalPlatformWindowInsets provides newInsets) {
                    App()
                }
            }
        }
    }
}
