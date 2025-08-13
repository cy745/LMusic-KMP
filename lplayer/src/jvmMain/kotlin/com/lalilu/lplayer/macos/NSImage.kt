package com.lalilu.lplayer.macos

import org.rococoa.NamedArg
import org.rococoa.cocoa.foundation.NSData
import org.rococoa.cocoa.foundation.NSObject
import org.rococoa.cocoa.foundation.NSSize

abstract class NSImage : NSObject() {
    companion object {
        fun alloc(): NSImage = nsAlloc()
    }

    abstract fun initWithData(data: NSData): NSImage
    abstract fun initWithCGImage(cgImage: CGImage, @NamedArg("size") size: NSSize): NSImage
}