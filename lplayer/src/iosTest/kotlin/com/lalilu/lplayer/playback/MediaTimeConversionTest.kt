package com.lalilu.lplayer.playback

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMedia.CMTimeMake
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
class MediaTimeConversionTest {
    @Test fun millisecondsUseTheActualTimescale() {
        for (timescale in listOf(600, 1_000, 44_100, 48_000, 1_000_000_000)) {
            val time = CMTimeMake(12L * timescale, timescale)
            assertEquals(12_000.0, time.useContents { toMilliseconds() })
        }
    }

    @Test fun fractionalSecondsArePreserved() {
        assertEquals(1_500.0, CMTimeMake(900, 600).useContents { toMilliseconds() })
    }

    @Test fun invalidTimescaleDoesNotDivideByZero() {
        assertEquals(0.0, CMTimeMake(0, 0).useContents { toMilliseconds() })
    }
}
