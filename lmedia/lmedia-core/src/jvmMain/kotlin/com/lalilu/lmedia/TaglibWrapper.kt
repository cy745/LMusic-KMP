package com.lalilu.lmedia

import com.lalilu.lmedia.domain.model.Metadata
import org.scijava.nativelib.NativeLoader

object TaglibWrapper {
    init {
        // NativeLoader 只会抽取「被请求的那一个」库，所以 tag.dll 的依赖必须先在这里显式加载一次，
        // 它才会被抽到与 tag.dll 相同的临时目录，之后 tag.dll 才能解析到它。
        // - Windows：构建动态链接 z.dll（上游 zlib 1.3.2，CMake OUTPUT_NAME=z，见 docs/native-binaries.md）
        // - macOS/Linux：zlib 由系统提供，这里会加载失败并被忽略
        runCatching { NativeLoader.loadLibrary("z") }
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