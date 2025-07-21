package com.lalilu.lmusic

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.vinceglb.filekit.FileKit

fun main() = application {
    FileKit.init(appId = "LMusic")

    Window(
        onCloseRequest = ::exitApplication,
        title = "LMusic-KMP",
    ) {
        App()
    }
}