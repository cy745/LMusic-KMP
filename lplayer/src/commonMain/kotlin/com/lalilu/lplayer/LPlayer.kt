package com.lalilu.lplayer

import com.lalilu.lplayer.playback.Playback
import org.koin.mp.KoinPlatform

class LPlayer {
    companion object {
        val instance: Playback by KoinPlatform.getKoin()
            .inject<Playback>()
    }
}
