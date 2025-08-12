package com.lalilu.lplayer.macos

import org.rococoa.ID
import org.rococoa.NamedArg
import org.rococoa.Selector
import org.rococoa.cocoa.foundation.NSObject

abstract class MPRemoteCommand : NSObject() {
    companion object {
        fun alloc(): MPRemoteCommand = nsAlloc()
    }

    /**
     * - (MPRemoteCommandHandlerStatus) handleCommand: (MPRemoteCommandEvent*) event;
     */
    abstract fun addTarget(target: ID, @NamedArg("action") selector: Selector)

    abstract fun enabled(): Boolean
    abstract fun setEnabled(enabled: Boolean)
}