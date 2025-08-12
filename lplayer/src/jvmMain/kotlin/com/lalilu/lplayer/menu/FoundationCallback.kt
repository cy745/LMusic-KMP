package com.lalilu.lplayer.menu

import org.rococoa.Foundation
import org.rococoa.ObjCObject
import org.rococoa.Rococoa
import org.rococoa.Selector
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KFunction

data class FoundationCallback(
    val target: ObjCObject,
    val selector: Selector
) {
    companion object {
        val REFERENCE_MAP: MutableMap<ObjCObject, Any> = ConcurrentHashMap<ObjCObject, Any>()

        inline fun <reified T : Any> wrap(
            callback: T,
            method: KFunction<*>? = null,
        ): FoundationCallback {
            val objcObject = Rococoa.proxy(callback)
            val selector = Foundation.selector("${method?.name ?: "invoke"}:")

            REFERENCE_MAP.put(objcObject, callback)

            return FoundationCallback(
                target = objcObject,
                selector = selector
            )
        }
    }
}
