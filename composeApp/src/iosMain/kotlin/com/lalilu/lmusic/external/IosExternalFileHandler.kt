package com.lalilu.lmusic.external

import io.github.vinceglb.filekit.PlatformFile
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL

/** Keeps the original NSURL so its security scope survives the Swift-to-Kotlin handoff. */
object IosExternalFileHandler {
    fun handle(url: NSURL) {
        KoinPlatform.getKoin()
            .get<ExternalAudioOpenCoordinator>()
            .submit(PlatformFile(url))
    }
}
