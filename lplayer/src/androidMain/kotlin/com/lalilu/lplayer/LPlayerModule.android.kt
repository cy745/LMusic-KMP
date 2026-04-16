package com.lalilu.lplayer

import com.lalilu.common.ext.reverseInject
import com.lalilu.lplayer.playback.MPlayerPlayback
import com.lalilu.lplayer.playback.Playback
import org.koin.core.module.Module
import org.koin.dsl.module

actual val playbackModule: Module = module {
    single<Playback>(createdAtStart = true) { ::MPlayerPlayback.reverseInject() }
}