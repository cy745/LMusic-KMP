package com.lalilu.lmedia

import com.lalilu.lmedia.entity.Metadata

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Taglib {
    actual fun version(): String {
        return "IOS"
    }

    actual suspend fun readMetadata(fd: Int): Metadata? {
        return null
    }

    actual suspend fun getLyric(fd: Int): String? {
        return null
    }

    actual suspend fun getPicture(fd: Int): ByteArray? {
        return null
    }

    actual suspend fun readMetadata(path: String): Metadata? {
        return null
    }

    actual suspend fun getLyric(path: String): String? {
        return null
    }

    actual suspend fun getPicture(path: String): ByteArray? {
        return null
    }
}

