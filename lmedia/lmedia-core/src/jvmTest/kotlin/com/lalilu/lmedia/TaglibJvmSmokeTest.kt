package com.lalilu.lmedia

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM 桌面端 Taglib JNI 冒烟测试：
 * 触发 NativeLoader 加载 libtag（平台原生活），readMetadata 读取真实 FLAC。
 */
class TaglibJvmSmokeTest {

    @Test
    fun smoke() = runBlocking {
        val version = Taglib.version()
        println("taglib version: $version")
        assertTrue(version.isNotBlank())

        val dir = System.getenv("LMUSIC_TEST_MUSIC") ?: "/Users/miku/Music/LMusic-Test"
        val files: Array<File>? = File(dir).listFiles()
        val file = files?.firstOrNull { it.extension == "flac" }
            ?: error("no flac found in $dir")
        println("testing file: ${file.name}")

        val metadata = Taglib.readMetadata(file.absolutePath)
        println("metadata title=${metadata?.title} artist=${metadata?.artist} duration=${metadata?.duration}ms")
        assertNotNull(metadata)
        assertTrue(metadata.duration > 0)
    }
}
