package com.lalilu.lmusic

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit
import org.koin.core.context.startKoin

fun main() = application {
    FileKit.init(appId = "LMusic")
    startKoin { koinSetup() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "LMusic-KMP",
    ) {
        platformSetupCoil()
        App()
    }
}