package com.lalilu.lmedia

import com.lalilu.lmedia.entity.Metadata

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object Taglib {
    actual fun version(): String = TaglibWrapper.version()
    actual suspend fun readMetadata(fd: Int): Metadata? = TaglibWrapper.readMetadataWithFD(fd)
    actual suspend fun readMetadata(path: String): Metadata? = TaglibWrapper.readMetadataWithPath(path)
    actual suspend fun getLyric(fd: Int): String? = TaglibWrapper.getLyricWithFD(fd)
    actual suspend fun getLyric(path: String): String? = TaglibWrapper.getLyricWithPath(path)
    actual suspend fun getPicture(fd: Int): ByteArray? = TaglibWrapper.getPictureWithFD(fd)
    actual suspend fun getPicture(path: String): ByteArray? = TaglibWrapper.getPictureWithPath(path)
}