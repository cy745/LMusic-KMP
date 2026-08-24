/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.lplayer.playback

import com.lalilu.llyricview.calibration.LyricCalibrationAudioConfig
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** 生成固定节奏的「轻、轻、轻、重」鼓点 PCM，供校准和试听共享。 */
internal object CalibrationDrumTrackGenerator {
    fun generate(
        config: LyricCalibrationAudioConfig,
        sampleRateHz: Int = 44_100,
    ): Pcm16AudioClip {
        val frameCount = (config.durationMs * sampleRateHz / 1_000L).toInt()
        val samples = ShortArray(frameCount)

        repeat(config.beatCount) { beatIndex ->
            val startFrame = (config.beatTimeMs(beatIndex) * sampleRateHz / 1_000L).toInt()
            val accent = config.isAccentBeat(beatIndex)
            // 前三拍使用短促的鼓边/高音鼓质感，最后一拍使用带下落音高和瞬态噪声的低鼓。
            val beatDurationMs = if (accent) 190 else 95
            val beatFrameCount = sampleRateHz * beatDurationMs / 1_000

            repeat(beatFrameCount) sample@{ offset ->
                val frame = startFrame + offset
                if (frame !in samples.indices) return@sample
                val seconds = offset.toDouble() / sampleRateHz
                val value = if (accent) {
                    synthesizeAccentDrum(seconds = seconds, beatIndex = beatIndex)
                } else {
                    synthesizeLightDrum(seconds = seconds, beatIndex = beatIndex)
                }
                samples[frame] = (value * Short.MAX_VALUE)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }

        return Pcm16AudioClip(
            samples = samples,
            sampleRateHz = sampleRateHz,
            channelCount = 1,
        )
    }

    /** 短促的鼓边与高音鼓混合，用作前三个弱拍。 */
    private fun synthesizeLightDrum(seconds: Double, beatIndex: Int): Double {
        val bodyPhase = 2.0 * PI * (215.0 * seconds - 180.0 * seconds * seconds)
        val body = sin(bodyPhase) * exp(-seconds * 34.0) * 0.48
        val rim = sin(2.0 * PI * 940.0 * seconds) * exp(-seconds * 72.0) * 0.22
        val noise = metallicNoise(seconds, beatIndex) * exp(-seconds * 58.0) * 0.16
        return (body + rim + noise).coerceIn(-1.0, 1.0)
    }

    /** 带快速降调的低鼓与短瞬态，用作每小节最后一个重拍。 */
    private fun synthesizeAccentDrum(seconds: Double, beatIndex: Int): Double {
        val frequencyDropPhase = 2.0 * PI * (
                50.0 * seconds + 145.0 / 18.0 * (1.0 - exp(-18.0 * seconds))
                )
        val kick = sin(frequencyDropPhase) * exp(-seconds * 14.0) * 0.78
        val body = sin(2.0 * PI * 108.0 * seconds) * exp(-seconds * 21.0) * 0.24
        val attack = metallicNoise(seconds, beatIndex) * exp(-seconds * 62.0) * 0.20
        return (kick + body + attack).coerceIn(-1.0, 1.0)
    }

    /** 使用确定性的非谐波组合制造鼓皮瞬态，保证每次生成完全相同的 PCM。 */
    private fun metallicNoise(seconds: Double, beatIndex: Int): Double {
        val phase = beatIndex * 0.73
        return sin(2.0 * PI * 3_701.0 * seconds + phase) *
                sin(2.0 * PI * 6_151.0 * seconds + 0.31 + phase)
    }
}
