package com.lalilu.wrapper

import com.sun.jna.Library
import com.sun.jna.Native

interface MediaPlayerLibrary : Library {
    companion object {
        fun load() {
            Native.load("MediaPlayer", MediaPlayerLibrary::class.java)
        }
    }
}