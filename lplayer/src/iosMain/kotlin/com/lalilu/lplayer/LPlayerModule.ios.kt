package com.lalilu.lplayer

import com.lalilu.common.ext.reverseInject
import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.AVPlayerPlayback
import org.koin.core.module.Module
import org.koin.dsl.module

actual val playbackModule: Module = module {
    single<Playback>(createdAtStart = true) { ::AVPlayerPlayback.reverseInject() }
}