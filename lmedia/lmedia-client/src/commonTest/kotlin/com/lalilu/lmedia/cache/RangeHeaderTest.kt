package com.lalilu.lmedia.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `Range` 头解析：这是"能不能正常 seek"的第一道关口，逐条钉死。 */
class RangeHeaderTest {

    private val total = 1000L

    @Test
    fun `treats missing or unparsable headers as a whole file request`() {
        assertEquals(RangeDisposition.WHOLE_FILE, RangeHeader.resolve(null, total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, RangeHeader.resolve("", total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, RangeHeader.resolve("items=0-1", total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, RangeHeader.resolve("bytes=abc-def", total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, RangeHeader.resolve("bytes=-", total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, RangeHeader.resolve("bytes=100-50", total).disposition)
    }

    @Test
    fun `resolves the probe range that AVPlayer sends first`() {
        val resolution = RangeHeader.resolve("bytes=0-1", total)

        assertEquals(RangeDisposition.SATISFIABLE, resolution.disposition)
        assertEquals(0L, resolution.range?.start)
        assertEquals(1L, resolution.range?.endInclusive)
        assertEquals(2L, resolution.range?.length)
    }

    @Test
    fun `resolves open ended and suffix ranges`() {
        val openEnded = RangeHeader.resolve("bytes=500-", total)
        assertEquals(500L, openEnded.range?.start)
        assertEquals(999L, openEnded.range?.endInclusive)

        val suffix = RangeHeader.resolve("bytes=-100", total)
        assertEquals(900L, suffix.range?.start)
        assertEquals(999L, suffix.range?.endInclusive)
    }

    @Test
    fun `clamps an end beyond the file length but rejects a start beyond it`() {
        val clamped = RangeHeader.resolve("bytes=900-5000", total)
        assertEquals(RangeDisposition.SATISFIABLE, clamped.disposition)
        assertEquals(999L, clamped.range?.endInclusive)

        val beyond = RangeHeader.resolve("bytes=5000-", total)
        assertEquals(RangeDisposition.UNSATISFIABLE, beyond.disposition)
        assertNull(beyond.range)
    }

    @Test
    fun `falls back to the whole file when the length is unknown or multiple ranges are requested`() {
        assertEquals(RangeDisposition.WHOLE_FILE, RangeHeader.resolve("bytes=0-1", 0L).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, RangeHeader.resolve("bytes=0-1,5-6", total).disposition)
    }

    @Test
    fun `extracts the total size from a content range header`() {
        assertEquals(32_239_619L, RangeHeader.totalSizeFromContentRange("bytes 0-1/32239619"))
        assertEquals(1000L, RangeHeader.totalSizeFromContentRange("bytes 0-99/1000"))
        assertNull(RangeHeader.totalSizeFromContentRange("bytes */0"))
        assertNull(RangeHeader.totalSizeFromContentRange(null))
        assertNull(RangeHeader.totalSizeFromContentRange("bytes 0-1/*"))
    }

    @Test
    fun `extracts the start offset from a content range header`() {
        assertEquals(0L, RangeHeader.startFromContentRange("bytes 0-1/32239619"))
        assertEquals(1024L, RangeHeader.startFromContentRange("bytes 1024-2047/32239619"))
        assertEquals(7L, RangeHeader.startFromContentRange("bytes 7-7/*"))
        assertNull(RangeHeader.startFromContentRange("bytes */0"))
        assertNull(RangeHeader.startFromContentRange(null))
        assertNull(RangeHeader.startFromContentRange("garbage"))
    }
}
