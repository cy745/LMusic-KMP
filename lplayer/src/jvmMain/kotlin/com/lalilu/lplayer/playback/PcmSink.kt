package com.lalilu.lplayer.playback

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** Experience output format. This is deliberately not advertised as original/bit-perfect output. */
internal const val PCM_RATE = 48000
internal const val PCM_FRAME_BYTES = 4
internal interface PcmSink {
    val playedFrames: Long
    fun write(bytes: ByteArray)
    fun start()
    fun pause()
    fun flush()
    fun close()
}
internal class JavaSoundPcmSink : PcmSink {
    private val requestedFormat = AudioFormat(PCM_RATE.toFloat(), 16, 2, true, false)
    private val line = AudioSystem.getSourceDataLine(requestedFormat).apply {
        // Short output latency; several seconds of preloading remain in bounded decoder queues.
        open(requestedFormat, PCM_RATE * PCM_FRAME_BYTES / 20)
    }
    override val playedFrames: Long get() = line.longFramePosition
    override fun write(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = line.write(bytes, offset, bytes.size - offset)
            check(count > 0) { "Audio output stopped accepting samples" }
            offset += count
        }
    }
    override fun start() = line.start()
    override fun pause() = line.stop()
    override fun flush() = line.flush()
    override fun close() { line.stop(); line.flush(); line.close() }
}
