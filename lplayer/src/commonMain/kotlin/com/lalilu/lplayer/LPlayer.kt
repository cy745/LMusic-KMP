package com.lalilu.lplayer

import com.lalilu.lplayer.playback.Playback
import com.lalilu.lplayer.playback.platformPlayback
import org.koin.core.annotation.Single


@Single(createdAtStart = true)
class LPlayer() : Playback by platformPlayback() {

    init {
        instance = this
    }

    companion object {
        lateinit var instance: LPlayer
            private set
    }
}

