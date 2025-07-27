package com.lalilu.lmusic

import com.lalilu.lmedia.LMediaModule
import org.koin.core.KoinApplication
import org.koin.ksp.generated.module

fun KoinApplication.koinSetup() {
    modules(LMediaModule.module)
}