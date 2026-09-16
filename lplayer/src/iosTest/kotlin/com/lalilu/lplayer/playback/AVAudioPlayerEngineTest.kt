package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.math.abs

/**
 * Bytes 播放路径此前完全静默：构造失败不读错误指针、`prepareToPlay` 返回值不查、解码错误只打日志，
 * 于是失败既不上报也不入账。这里用真实 AVAudioPlayer 固定两条边界：坏数据必须抛错并写入状态，
 * 合法 PCM WAV 必须正常加载（防止"改成永远抛错"也算通过）。
 */
class AVAudioPlayerEngineTest {
    private val audio = LAudio(id = "bytes-1", title = "bytes", mediaSourceName = "local")

    @Test fun invalidBytesFailTheLoadInsteadOfSilentlySucceeding() = runBlocking {
        val engine = AVAudioPlayerEngine()
        try {
            assertFailsWith<IllegalStateException> {
                engine.load(MediaData.Bytes("this is not audio".encodeToByteArray()), audio)
            }
            assertNotNull(engine.state.value.error, "失败的加载必须留下可记账的错误状态")
            assertTrue(!engine.state.value.isLoading)
        } finally {
            engine.release()
        }
    }

    @Test fun emptyDataFailsTheLoad() = runBlocking {
        val engine = AVAudioPlayerEngine()
        try {
            assertFailsWith<IllegalStateException> {
                engine.load(MediaData.Bytes(ByteArray(0)), audio)
            }
            assertTrue(engine.state.value.error != null, "空数据也必须留下错误状态")
        } finally {
            engine.release()
        }
    }

    @Test fun validPcmWavLoadsWithItsRealDuration() = runBlocking {
        val engine = AVAudioPlayerEngine()
        try {
            engine.load(MediaData.Bytes(pcmWav(durationMillis = 400)), audio)
            val state = engine.state.value
            assertNull(state.error)
            assertTrue(!state.isLoading)
            assertTrue(abs(state.duration - 400L) < 60L, "unexpected duration: ${state.duration}")
            assertEquals(0L, engine.currentPosition())
        } finally {
            engine.release()
        }
    }

    /** 16bit / 单声道 / 8kHz 的静音 PCM WAV，够真实 AVAudioPlayer 解析。 */
    private fun pcmWav(durationMillis: Int, sampleRate: Int = 8_000): ByteArray {
        val samples = sampleRate * durationMillis / 1000
        val dataSize = samples * 2
        val out = ByteArray(44 + dataSize)

        fun ascii(offset: Int, value: String) {
            value.forEachIndexed { index, char -> out[offset + index] = char.code.toByte() }
        }

        fun int32(offset: Int, value: Int) {
            repeat(4) { byte -> out[offset + byte] = ((value shr (8 * byte)) and 0xFF).toByte() }
        }

        fun int16(offset: Int, value: Int) {
            repeat(2) { byte -> out[offset + byte] = ((value shr (8 * byte)) and 0xFF).toByte() }
        }

        ascii(0, "RIFF")
        int32(4, 36 + dataSize)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        int32(16, 16)
        int16(20, 1)
        int16(22, 1)
        int32(24, sampleRate)
        int32(28, sampleRate * 2)
        int16(32, 2)
        int16(34, 16)
        ascii(36, "data")
        int32(40, dataSize)
        return out
    }
}
