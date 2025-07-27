package com.lalilu.lmusic

import org.koin.core.context.startKoin

fun startKoin() {
    startKoin { koinSetup() }
}