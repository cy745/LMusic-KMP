package com.lalilu.lmusic

import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lplayer.playback.MPlayerPlayback
import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.PlaybackHistory
import org.koin.core.module.Module as KoinModuleType
import org.koin.dsl.module

/**
 * Android-specific Koin definitions.
 *
 * Workaround: koin-ksp compiler (cy745 fork 2.3.1) doesn't generate
 * module definitions for the Android target in the lplayer module.
 */
actual fun platformKoinModule(): KoinModuleType = module {
    single<Playback> {
        MPlayerPlayback(
            context = get(),
            audioRepository = get(),
            history = get()
        )
    }
}
