package com.lalilu.lmusic

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    platformSetupCoil()
    App()
}