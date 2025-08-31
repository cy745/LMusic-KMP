package com.lalilu.lplayer.helper

import com.lalilu.cinterop.ObserverProtocol
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.Foundation.addObserver
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private class Observer(
    private val callback: () -> Unit = {}
) : NSObject(), ObserverProtocol {
    override fun observeValueForKeyPath(
        keyPath: String?,
        ofObject: Any?,
        change: Map<Any?, *>?,
        context: COpaquePointer?
    ) {
        callback()
    }
}

@OptIn(ExperimentalForeignApi::class)
fun <T : NSObject> T.observeFor(
    keyPath: String,
    options: ULong = NSKeyValueObservingOptionNew,
    context: COpaquePointer? = null,
    callback: (T) -> Unit
) {
    this.addObserver(
        observer = Observer { callback(this) },
        forKeyPath = keyPath,
        options = options,
        context = context
    )
}