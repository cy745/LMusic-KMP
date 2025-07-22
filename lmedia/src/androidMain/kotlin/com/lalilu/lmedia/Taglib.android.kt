package com.lalilu.lmedia


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Taglib {

    init {
        System.loadLibrary("tag")
    }

    actual fun test(): String {
        return "Android"
    }
}