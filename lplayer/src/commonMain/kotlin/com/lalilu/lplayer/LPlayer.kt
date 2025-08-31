package com.lalilu.lplayer

import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.platformPlayback
import org.koin.core.annotation.Single
import org.koin.mp.KoinPlatform


@Single(createdAtStart = true)
class LPlayer() : Playback by platformPlayback() {

    companion object {
        val instance: LPlayer by KoinPlatform.getKoin()
            .inject<LPlayer>()
    }
}

