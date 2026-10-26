package com.lalilu.lmusic.prototype

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Test-owned quiet PCM fixtures; never touches the user's music files. */
object PrototypeAudio {
    fun write(directory: File, name: String, frequency: Double = 220.0, durationMs: Int = 8000): File {
        directory.mkdirs()
        require(durationMs in 1..600_000) { "Test fixture duration must be within ten minutes" }
        val frames = (16000L * durationMs / 1000).toInt()
        val size = frames * 2
        val data = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray())
        data.putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
        data.putShort(2).putShort(16).put("data".toByteArray()).putInt(size)
        repeat(frames) { n -> data.putShort((sin(2 * PI * frequency * n / 16000) * 2000).toInt().toShort()) }
        return File(directory, "$name.wav").apply { writeBytes(data.array()) }
    }
}
