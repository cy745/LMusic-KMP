package com.lalilu.lplayer.macos

import org.rococoa.cocoa.foundation.NSNumber
import org.rococoa.cocoa.foundation.NSObject

abstract class MPRemoteCommandEvent : NSObject() {
    companion object {
        fun alloc(): MPRemoteCommandEvent = nsAlloc()
    }

    abstract fun command(): MPRemoteCommand
    abstract fun timestamp(): NSTimeInterval
}

abstract class NSTimeInterval : NSNumber()


abstract class MPChangePlaybackPositionCommandEvent : MPRemoteCommandEvent() {
    abstract fun positionTime(): NSTimeInterval
}