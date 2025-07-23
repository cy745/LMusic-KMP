package com.lalilu.lmedia

import org.scijava.nativelib.NativeLoader

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Taglib {

    init {
        NativeLoader.loadLibrary("tag")
    }

    actual fun test(): String {
        return "JVM, Taglib version: ${getVersion()}"
    }

    external fun getVersion(): String
}

