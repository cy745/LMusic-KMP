package com.lalilu.lplayer.playback

actual fun platformPlayback(): Playback {
    return AVPlayerPlayback()
}