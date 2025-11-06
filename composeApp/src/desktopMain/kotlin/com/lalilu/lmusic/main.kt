package com.lalilu.lmusic

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit
import org.koin.core.context.startKoin

fun main() {
    FileKit.init(appId = "LMusic")
    startKoin { koinSetup() }

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
            platformSetupCoil()
            App()
        }
    }
}