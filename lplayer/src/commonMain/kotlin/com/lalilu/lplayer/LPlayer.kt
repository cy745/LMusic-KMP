package com.lalilu.lplayer

import com.lalilu.lmedia.source.Library
import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.platformPlayback
import org.koin.core.annotation.Single
import org.koin.mp.KoinPlatform


@Single(createdAtStart = true)
class LPlayer(library: Library) : Playback by platformPlayback(library) {

    companion object {
        val instance: LPlayer by KoinPlatform.getKoin()
            .inject<LPlayer>()
    }
}

