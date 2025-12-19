package com.lalilu.lplayer.playback

import com.lalilu.lmedia.source.Library

actual fun platformPlayback(library: Library): Playback = MPlayerPlayback(library)