package com.lalilu.lplayer.macos

import com.sun.jna.Callback
import com.sun.jna.Structure
import org.rococoa.Rococoa
import org.rococoa.cocoa.CGFloat
import org.rococoa.cocoa.foundation.NSObject

data class CGSize(
    val width: CGFloat,
    val height: CGFloat
) : Structure(), Structure.ByValue

fun interface RequestHandler : Callback {
    fun invoke(size: CGSize): NSImage
}

abstract class MPMediaItemArtwork : NSObject() {

    companion object {
        fun initWithBoundsSize(
            boundsSize: CGSize,
            callback: RequestHandler
        ): MPMediaItemArtwork {
            return Rococoa.create(
                "MPMediaItemArtwork", MPMediaItemArtwork::class.java,
                "initWithBoundsSize:requestHandler:",
                boundsSize,
                callback
            )
        }
    }
}