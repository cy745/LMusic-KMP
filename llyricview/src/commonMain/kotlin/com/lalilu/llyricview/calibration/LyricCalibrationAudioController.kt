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

package com.lalilu.llyricview.calibration

import kotlinx.coroutines.flow.StateFlow

/** 校准音轨的节拍参数；播放实现与界面必须共享它，才能使用同一组理论节拍时间。 */
data class LyricCalibrationAudioConfig(
    val bpm: Int = 108,
    val leadInMs: Long = 1_000L,
    val beatsPerMeasure: Int = 4,
    val accentBeatIndex: Int = 3,
    // 需要 8 个样本，但额外保留一个重音和尾部静音，允许漏点一次，也避免蓝牙延迟使
    // 最后一个可听重音落在 AudioTrack 已结束之后。
    val beatCount: Int = 40,
    val sampleStartBeat: Int = 7,
    val requiredTapCount: Int = 8,
) {
    init {
        require(bpm > 0)
        require(leadInMs >= 0L)
        require(beatsPerMeasure > 0)
        require(accentBeatIndex in 0 until beatsPerMeasure)
        require(beatCount > 0)
        require(sampleStartBeat in 0 until beatCount)
        require(isAccentBeat(sampleStartBeat))
        require(requiredTapCount > 0)
        require((sampleStartBeat until beatCount).count(::isAccentBeat) >= requiredTapCount)
    }

    val beatIntervalMs: Long get() = 60_000L / bpm
    val durationMs: Long get() = leadInMs + beatCount * beatIntervalMs
    fun beatTimeMs(index: Int): Long = leadInMs + index * beatIntervalMs
    fun isAccentBeat(index: Int): Boolean =
        ((index % beatsPerMeasure) + beatsPerMeasure) % beatsPerMeasure == accentBeatIndex

    fun nextAccentBeatAtOrAfter(index: Int): Int {
        val beatInMeasure = ((index % beatsPerMeasure) + beatsPerMeasure) % beatsPerMeasure
        val distance = (accentBeatIndex - beatInMeasure + beatsPerMeasure) % beatsPerMeasure
        return index + distance
    }
}

sealed interface LyricCalibrationAudioState {
    data object Idle : LyricCalibrationAudioState
    data class Playing(val durationMs: Long) : LyricCalibrationAudioState
    data class Failed(val message: String) : LyricCalibrationAudioState
}

/**
 * `llyricview` 使用的最小音频控制协议。
 *
 * 该协议刻意不依赖 `lplayer`：项目中是 `lplayer -> llyricview` 的单向依赖，Android
 * 端由 `lplayer` 组合旁路播放器与主歌曲播放器后实现本协议，从而避免模块循环依赖。
 */
interface LyricCalibrationAudioController {
    val state: StateFlow<LyricCalibrationAudioState>

    /** 开始校准时会临时暂停主播放器；结束或离开页面后恢复原先的播放状态。 */
    suspend fun start(config: LyricCalibrationAudioConfig)

    fun stop()

    /** 返回校准音轨当前的实际播放头位置。 */
    fun currentPositionMs(): Long
}
