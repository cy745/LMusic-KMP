package com.lalilu.lplayer.macos

import org.rococoa.cocoa.foundation.NSObject

abstract class MPMediaItemArtwork : NSObject() {

    companion object {
        fun alloc(): MPMediaItemArtwork = nsAlloc()
    }
}