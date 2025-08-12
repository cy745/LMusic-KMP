package com.lalilu.lplayer.macos

import org.rococoa.Rococoa
import org.rococoa.cocoa.foundation.NSObject

inline fun <reified T : NSObject> nsAlloc(): T {
    return Rococoa.create(T::class.java.simpleName, T::class.java)
}

inline fun <reified T : NSObject> nsObtain(ocMethodName: String): T {
    return Rococoa.create(T::class.java.simpleName, T::class.java, ocMethodName)
}
