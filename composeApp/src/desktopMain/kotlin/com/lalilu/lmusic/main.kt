package com.lalilu.lmusic

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LMusic-KMP",
    ) {
        App()
    }
}