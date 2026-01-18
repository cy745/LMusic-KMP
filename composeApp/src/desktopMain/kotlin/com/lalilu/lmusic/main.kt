package com.lalilu.lmusic

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.formdev.flatlaf.FlatIntelliJLaf
import com.formdev.flatlaf.FlatLaf
import io.github.vinceglb.filekit.FileKit
import org.koin.core.context.startKoin
import java.awt.Toolkit

@OptIn(InternalComposeUiApi::class)
fun main() {
    FileKit.init(appId = "LMusic")
    startKoin { koinSetup() }
    platformSetupCoil()

    System.setProperty("apple.awt.application.appearance", "system")
    FlatLaf.setup(FlatIntelliJLaf())

    application {
        val windowState = WindowStateKeeper.rememberWindowState()

        Window(
            onCloseRequest = {
                WindowStateKeeper.saveWindowState(windowState)
                exitApplication()
            },
            state = windowState,
            title = "LMusic",
        ) {
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            window.background = if (isSystemInDarkTheme()) java.awt.Color.BLACK else java.awt.Color.WHITE

            val insets = LocalPlatformWindowInsets.current
            val newInsets = remember(insets) {
                object : PlatformWindowInsets by insets {
                    val windowInsets = Toolkit.getDefaultToolkit().getScreenInsets(window.graphicsConfiguration)

                    override val statusBars: PlatformInsets =
                        PlatformInsets(0, windowInsets.top, 0, 0)
                }
            }

            CompositionLocalProvider(LocalPlatformWindowInsets provides newInsets) {
                App()
            }
        }
    }
}