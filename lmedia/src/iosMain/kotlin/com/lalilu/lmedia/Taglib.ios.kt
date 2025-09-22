package com.lalilu.lmedia

import com.lalilu.lmedia.entity.Metadata
import com.lalilu.taglib.taglib_runtime_version
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Taglib {
    @OptIn(ExperimentalForeignApi::class)
    actual fun version(): String {
        return memScoped { taglib_runtime_version()?.toKString() }
            ?: "Unknown"
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

