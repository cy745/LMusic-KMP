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

import kotlin.math.abs
import kotlin.math.roundToLong

data class LyricCalibrationTap(
    val beatIndex: Int,
    /** 点击播放头位置减去理论节拍位置；正值表示用户听到节拍时播放头已经偏晚。 */
    val errorMs: Long,
)

data class LyricOffsetEstimate(
    /** 可以直接写入 [com.lalilu.llyricview.LyricSettings.timeOffset] 的值。 */
    val timeOffsetMs: Long,
    val medianTapErrorMs: Long,
    val acceptedSampleCount: Int,
    val rejectedSampleCount: Int,
    val spreadMs: Long,
)

/**
 * 将点击时间匹配到最近的可采样重音。同一个重音只接受一次点击，轻拍点击会被忽略，
 * 避免连点或跟随每一拍点击污染结果。
 */
fun matchCalibrationTap(
    positionMs: Long,
    config: LyricCalibrationAudioConfig,
    sampledBeatIndices: Set<Int>,
): LyricCalibrationTap? {
    // 只在理论重音之间寻找最近目标，不能先匹配最近的任意节拍。否则蓝牙延迟超过
    // 半拍时，用户跟随听到的重音点击会落到下一轻拍上，并被错误地忽略。
    val beatIndex = (config.sampleStartBeat until config.beatCount)
        .asSequence()
        .filter(config::isAccentBeat)
        .minByOrNull { beat -> abs(positionMs - config.beatTimeMs(beat)) }
        ?: return null
    val errorMs = positionMs - config.beatTimeMs(beatIndex)
    val maxUnambiguousErrorMs = config.beatIntervalMs * config.beatsPerMeasure / 2L
    if (abs(errorMs) > maxUnambiguousErrorMs) return null
    if (beatIndex in sampledBeatIndices) return null

    return LyricCalibrationTap(
        beatIndex = beatIndex,
        errorMs = errorMs,
    )
}

/**
 * 使用中位数与 MAD（中位绝对偏差）过滤误触，再对保留样本取均值。
 *
 * 现有歌词时钟使用 `播放位置 + timeOffset`。若点击比理论节拍晚 100ms，说明声音相对
 * 播放头晚了约 100ms，因此应保存 `-100ms`，而不是直接保存点击误差。
 */
fun estimateLyricTimeOffset(taps: List<LyricCalibrationTap>): LyricOffsetEstimate? {
    if (taps.size < 4) return null

    val errors = taps.map { it.errorMs }
    val median = errors.median()
    val mad = errors.map { abs(it - median) }.median()
    val threshold = maxOf(35L, mad * 3L)
    val accepted = errors.filter { abs(it - median) <= threshold }
    if (accepted.size < 4) return null

    val meanError = accepted.average().roundToLong()
    val acceptedMedian = accepted.median()
    val spread = accepted.map { abs(it - acceptedMedian) }.median()

    return LyricOffsetEstimate(
        timeOffsetMs = -meanError,
        medianTapErrorMs = acceptedMedian,
        acceptedSampleCount = accepted.size,
        rejectedSampleCount = errors.size - accepted.size,
        spreadMs = spread,
    )
}

private fun List<Long>.median(): Long {
    require(isNotEmpty())
    val values = sorted()
    val middle = values.size / 2
    return if (values.size % 2 == 1) {
        values[middle]
    } else {
        ((values[middle - 1] + values[middle]) / 2.0).roundToLong()
    }
}
