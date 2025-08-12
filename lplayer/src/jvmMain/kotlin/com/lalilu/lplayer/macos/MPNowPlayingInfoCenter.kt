package com.lalilu.lplayer.macos

import org.rococoa.cocoa.foundation.NSDictionary
import org.rococoa.cocoa.foundation.NSObject

abstract class MPNowPlayingInfoCenter : NSObject() {
    companion object {
        fun defaultCenter(): MPNowPlayingInfoCenter = nsObtain("defaultCenter")
    }

    abstract fun nowPlayingInfo(): NSDictionary?
    abstract fun setNowPlayingInfo(info: NSDictionary)
}