package com.lalilu.lmedia

import com.lalilu.lmedia.entity.Metadata
import org.scijava.nativelib.NativeLoader

object TaglibWrapper {
    init {
        runCatching { NativeLoader.loadLibrary("zlib1") }
        NativeLoader.loadLibrary("tag")
    }

    external fun version(): String
    external suspend fun readMetadataWithFD(fd: Int): Metadata?
    external suspend fun readMetadataWithPath(path: String): Metadata?
    external suspend fun getLyricWithFD(fd: Int): String?
    external suspend fun getLyricWithPath(path: String): String?
    external suspend fun getPictureWithFD(fd: Int): ByteArray?
    external suspend fun getPictureWithPath(path: String): ByteArray?
}