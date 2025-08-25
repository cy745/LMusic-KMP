package com.lalilu.lplayer.playback

import com.lalilu.lplayer.player.WrappedAVPlayer

actual fun platformPlayback(): Playback {
    return WrappedAVPlayer()
}