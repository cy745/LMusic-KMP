package com.lalilu.lplayer.playback

import com.lalilu.lmedia.data.Library

actual fun platformPlayback(library: Library): Playback = AVPlayerPlayback(library)