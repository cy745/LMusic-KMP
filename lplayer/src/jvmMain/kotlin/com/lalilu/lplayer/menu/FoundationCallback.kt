package com.lalilu.lplayer.menu

import org.rococoa.*
import java.util.concurrent.ConcurrentHashMap

data class FoundationCallback(
    val target: ObjCObject,
    val selector: Selector
)

object FoundationCallbackRegistry {
    private val REFERENCE_MAP: MutableMap<ObjCObject, ObjcCallback> = ConcurrentHashMap<ObjCObject, ObjcCallback>()

    fun registerCallback(callback: ObjcCallback): FoundationCallback {
        val objcObject = Rococoa.proxy(callback)
        REFERENCE_MAP.put(objcObject, callback)
        return FoundationCallback(objcObject, Foundation.selector("invoke:"))
    }

    fun unregister(callback: FoundationCallback) {
        REFERENCE_MAP.remove(callback.target)
    }
}

fun interface ObjcCallback {
    fun invoke(sender: ID)
}