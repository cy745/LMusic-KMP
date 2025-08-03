package com.lalilu.lplayer

import org.koin.core.annotation.Single


@Single(createdAtStart = true)
class LPlayer() {

    init {
        instance = this
    }

    companion object {
        lateinit var instance: LPlayer
            private set
    }
}

