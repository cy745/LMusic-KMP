package com.lalilu.lmedia.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `Range` 头解析：这是"能不能正常 seek"的第一道关口，逐条钉死。 */
class HttpRangeTest {

    private val total = 1000L

    @Test
    fun `treats missing or unparsable headers as a whole file request`() {
        assertEquals(RangeDisposition.WHOLE_FILE, HttpRange.resolve(null, total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, HttpRange.resolve("", total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, HttpRange.resolve("items=0-1", total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, HttpRange.resolve("bytes=abc-def", total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, HttpRange.resolve("bytes=-", total).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, HttpRange.resolve("bytes=100-50", total).disposition)
    }

    @Test
    fun `resolves the probe range that AVPlayer sends first`() {
        val resolution = HttpRange.resolve("bytes=0-1", total)

        assertEquals(RangeDisposition.SATISFIABLE, resolution.disposition)
        assertEquals(0L, resolution.range?.start)
        assertEquals(1L, resolution.range?.endInclusive)
        assertEquals(2L, resolution.range?.length)
    }

    @Test
    fun `resolves open ended and suffix ranges`() {
        val openEnded = HttpRange.resolve("bytes=500-", total)
        assertEquals(500L, openEnded.range?.start)
        assertEquals(999L, openEnded.range?.endInclusive)

        val suffix = HttpRange.resolve("bytes=-100", total)
        assertEquals(900L, suffix.range?.start)
        assertEquals(999L, suffix.range?.endInclusive)
    }

    @Test
    fun `clamps an end beyond the file length but rejects a start beyond it`() {
        val clamped = HttpRange.resolve("bytes=900-5000", total)
        assertEquals(RangeDisposition.SATISFIABLE, clamped.disposition)
        assertEquals(999L, clamped.range?.endInclusive)

        val beyond = HttpRange.resolve("bytes=5000-", total)
        assertEquals(RangeDisposition.UNSATISFIABLE, beyond.disposition)
        assertNull(beyond.range)
    }

    @Test
    fun `falls back to the whole file when the length is unknown or multiple ranges are requested`() {
        assertEquals(RangeDisposition.WHOLE_FILE, HttpRange.resolve("bytes=0-1", 0L).disposition)
        assertEquals(RangeDisposition.WHOLE_FILE, HttpRange.resolve("bytes=0-1,5-6", total).disposition)
    }

    @Test
    fun `extracts the total size from a content range header`() {
        assertEquals(32_239_619L, HttpRange.totalSizeFromContentRange("bytes 0-1/32239619"))
        assertEquals(1000L, HttpRange.totalSizeFromContentRange("bytes 0-99/1000"))
        assertNull(HttpRange.totalSizeFromContentRange("bytes */0"))
        assertNull(HttpRange.totalSizeFromContentRange(null))
        assertNull(HttpRange.totalSizeFromContentRange("bytes 0-1/*"))
    }
}
