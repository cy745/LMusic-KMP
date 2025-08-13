package com.lalilu.lplayer.macos

import org.rococoa.cocoa.foundation.NSObject

typealias NSTimeInterval = Double

abstract class MPRemoteCommandEvent : NSObject() {
    companion object {
        fun alloc(): MPRemoteCommandEvent = nsAlloc()
    }

    abstract fun command(): MPRemoteCommand
    abstract fun timestamp(): NSTimeInterval
}

abstract class MPChangePlaybackPositionCommandEvent : MPRemoteCommandEvent() {
    abstract fun positionTime(): NSTimeInterval
}