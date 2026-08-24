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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LyricOffsetEstimatorTest {
    private val config = LyricCalibrationAudioConfig()

    @Test
    fun matchTap_usesNearestBeatAndRejectsDuplicate() {
        val beat = config.sampleStartBeat
        val expectedTime = config.beatTimeMs(beat)

        assertEquals(
            LyricCalibrationTap(beatIndex = beat, errorMs = 72L),
            matchCalibrationTap(expectedTime + 72L, config, emptySet()),
        )
        assertNull(matchCalibrationTap(expectedTime + 72L, config, setOf(beat)))
        val outsideFirstAccentWindow =
            expectedTime - config.beatIntervalMs * config.beatsPerMeasure / 2L - 1L
        assertNull(matchCalibrationTap(outsideFirstAccentWindow, config, emptySet()))
    }

    @Test
    fun matchTap_matchesDelayedAccentInsteadOfRejectingItAsLightBeat() {
        val accentBeat = config.sampleStartBeat
        val delayedByMoreThanHalfBeat = config.beatIntervalMs * 3L / 4L

        assertEquals(
            LyricCalibrationTap(accentBeat, delayedByMoreThanHalfBeat),
            matchCalibrationTap(
                positionMs = config.beatTimeMs(accentBeat) + delayedByMoreThanHalfBeat,
                config = config,
                sampledBeatIndices = emptySet(),
            ),
        )
    }

    @Test
    fun estimate_invertsTapLatencyForExistingLyricClock() {
        val estimate = estimateLyricTimeOffset(
            listOf(96L, 100L, 104L, 102L, 98L, 101L, 99L, 103L)
                .mapIndexed { index, error -> LyricCalibrationTap(index, error) }
        )

        assertNotNull(estimate)
        assertEquals(-100L, estimate.timeOffsetMs)
        assertEquals(8, estimate.acceptedSampleCount)
    }

    @Test
    fun estimate_filtersAccidentalTap() {
        val estimate = estimateLyricTimeOffset(
            listOf(80L, 82L, 78L, 81L, 79L, 83L, -210L, 80L)
                .mapIndexed { index, error -> LyricCalibrationTap(index, error) }
        )

        assertNotNull(estimate)
        assertEquals(-80L, estimate.timeOffsetMs)
        assertEquals(1, estimate.rejectedSampleCount)
    }
}
