package com.lalilu.lplayer.playback

actual fun platformPlayback(): Playback {
    return VLCPlayback()
}