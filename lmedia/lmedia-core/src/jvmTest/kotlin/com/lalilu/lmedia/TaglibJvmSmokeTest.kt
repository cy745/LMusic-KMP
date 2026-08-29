package com.lalilu.lmedia

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM 桌面端 Taglib JNI 冒烟测试（自包含）：
 * 触发 NativeLoader 加载平台原生的 libtag（RegisterNatives 版），
 * 用运行时生成的 1 秒 WAV 验证 readMetadata 链路。
 * 不依赖外部文件，可在 CI 与任意开发机上运行。
 */
class TaglibJvmSmokeTest {

    @Test
    fun smoke() = runBlocking {
        val version = Taglib.version()
        println("taglib version: $version")
        assertTrue(version.isNotBlank(), "native libtag version should not be blank")

        val wav = File.createTempFile("lmusic-smoke", ".wav").apply { deleteOnExit() }
        writeMiniWav(wav, seconds = 1, sampleRate = 8000)

        val metadata = Taglib.readMetadata(wav.absolutePath)
        println("metadata duration=${metadata?.duration}ms")
        assertNotNull(metadata, "readMetadata should return a Metadata object")
        assertTrue(metadata.duration == 1000L, "1s wav duration should be 1000ms, got ${metadata.duration}")
    }

    /** 写一个最简单的 8bit 单声道 PCM WAV（44 字节头 + 数据）。 */
    private fun writeMiniWav(file: File, seconds: Int, sampleRate: Int) {
        val dataSize = seconds * sampleRate // 8bit mono: 1 字节/采样
        file.outputStream().use { out ->
            fun u32(v: Int) = out.write(byteArrayOf(
                (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
                ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
            ))
            fun u16(v: Int) = out.write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()))
            out.write("RIFF".toByteArray()); u32(36 + dataSize); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); u32(16); u16(1); u16(1)       // PCM, mono
            u32(sampleRate); u32(sampleRate * 1 * 8 / 8)                   // byte rate
            u16(1); u16(8)                                                 // block align, bits
            out.write("data".toByteArray()); u32(dataSize)
            out.write(ByteArray(dataSize))                                  // 静音
        }
    }
}
