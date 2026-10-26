package com.lalilu.playback.core

import kotlin.test.*

class OutputCapabilitiesTest {
    @Test fun advertisedHiResDoesNotProveOriginalOutput() {
        val report = OutputCapabilities(advertisedSampleRates = listOf(192000),
            apiFormat = OutputFormat(192000, 2, "PCM 24 bit"))
        assertEquals(OutputMode.Experience, report.actualMode(OutputMode.Original))
        assertNotNull(report.fallbackReason(OutputMode.Original))
    }
    @Test fun supportedPathMustActuallyBeActiveAndRequested() {
        val available = OutputCapabilities(original = Support.Supported)
        assertEquals(OutputMode.Experience, available.actualMode(OutputMode.Original))
        val active = available.copy(originalActive = true)
        assertEquals(OutputMode.Original, active.actualMode(OutputMode.Original))
        assertEquals(OutputMode.Experience, active.actualMode(OutputMode.Experience))
        assertNull(active.fallbackReason(OutputMode.Original))
    }
    @Test fun routeLosingVerifiedSupportFallsBackEvenWithStaleActiveFlag() {
        val report = OutputCapabilities(original = Support.Unsupported, originalActive = true,
            originalReason = "route no longer supports direct output")
        assertEquals(OutputMode.Experience, report.actualMode(OutputMode.Original))
        assertEquals(report.originalReason, report.fallbackReason(OutputMode.Original))
    }
}
