package com.lalilu.lplayer.helper

import com.lalilu.cinterop.ObserverProtocol
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.memScoped
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.Foundation.addObserver
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class Observer(
    private val callback: (
        keyPath: String?,
        ofObject: Any?,
        change: Map<Any?, *>?,
        context: COpaquePointer?
    ) -> Unit
) : NSObject(), ObserverProtocol {
    override fun observeValueForKeyPath(
        keyPath: String?,
        ofObject: Any?,
        change: Map<Any?, *>?,
        context: COpaquePointer?
    ) {
        callback(keyPath, ofObject, change, context)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun <T : NSObject> T.observeFor(
    observer: Observer,
    keyPath: String,
    options: ULong = NSKeyValueObservingOptionNew,
    context: COpaquePointer? = null,
) {
    this.addObserver(
        observer = observer,
        forKeyPath = keyPath,
        options = options,
        context = context
    )
}

@OptIn(ExperimentalForeignApi::class)
fun Any.cOpaquePtr(): COpaquePointer {
    return memScoped { StableRef.create(this).asCPointer() }
}