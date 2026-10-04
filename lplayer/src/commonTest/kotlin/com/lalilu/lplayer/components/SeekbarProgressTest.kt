package com.lalilu.lplayer.components

import kotlin.test.Test
import kotlin.test.assertEquals

class SeekbarProgressTest {
    @Test
    fun unknownOrInvalidDurationDoesNotProduceProgress() {
        for (duration in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(0f, seekbarFraction(3000f, 0f, duration))
        }
        assertEquals(0f, seekbarFraction(Float.NaN, 0f, 3000f))
    }

    @Test
    fun knownDurationUsesAbsolutePlaybackAndBufferEndWithClamping() {
        assertEquals(0.25f, seekbarFraction(1000f, 0f, 4000f))
        assertEquals(0.75f, seekbarFraction(3000f, 0f, 4000f))
        assertEquals(1f, seekbarFraction(5000f, 0f, 4000f))
        assertEquals(0f, seekbarFraction(-10f, 0f, 4000f))
    }
}
