package com.lalilu.lmedia.source.webdav

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 提取决策矩阵。
 *
 * 两个节点（30% / 100%）的取舍直接决定"听歌时会不会多读一次标签"以及"时长与封面能不能拿到"，
 * 所以这里把每条边界都钉住。
 */
class WebDavExtractionDecisionTest {

    private val total = 1000L
    private val fingerprint = "1000|\"etag\""

    @Test
    fun `skips before reaching the partial threshold`() {
        assertEquals(
            ExtractionDecision.SKIP,
            decideExtraction(
                filled = 299,
                total = total,
                existingFingerprint = null,
                existingComplete = false,
                currentFingerprint = fingerprint,
            ),
        )
    }

    @Test
    fun `partially extracts exactly at the thirty percent threshold`() {
        assertEquals(
            ExtractionDecision.PARTIAL,
            decideExtraction(
                filled = 300,
                total = total,
                existingFingerprint = null,
                existingComplete = false,
                currentFingerprint = fingerprint,
            ),
        )
    }

    @Test
    fun `fully extracts once the file is complete`() {
        assertEquals(
            ExtractionDecision.FULL,
            decideExtraction(
                filled = 1000,
                total = total,
                existingFingerprint = null,
                existingComplete = false,
                currentFingerprint = fingerprint,
            ),
        )
    }

    @Test
    fun `upgrades a partial record to a full one at completion`() {
        assertEquals(
            ExtractionDecision.FULL,
            decideExtraction(
                filled = 1000,
                total = total,
                existingFingerprint = fingerprint,
                existingComplete = false,
                currentFingerprint = fingerprint,
            ),
        )
    }

    @Test
    fun `skips when the complete record already matches the fingerprint`() {
        assertEquals(
            ExtractionDecision.SKIP,
            decideExtraction(
                filled = 1000,
                total = total,
                existingFingerprint = fingerprint,
                existingComplete = true,
                currentFingerprint = fingerprint,
            ),
        )
        assertEquals(
            ExtractionDecision.SKIP,
            decideExtraction(
                filled = 500,
                total = total,
                existingFingerprint = fingerprint,
                existingComplete = false,
                currentFingerprint = fingerprint,
            ),
        )
    }

    @Test
    fun `re-extracts when the remote file changed`() {
        assertEquals(
            ExtractionDecision.PARTIAL,
            decideExtraction(
                filled = 400,
                total = total,
                existingFingerprint = "1000|\"old-etag\"",
                existingComplete = true,
                currentFingerprint = fingerprint,
            ),
        )
        assertEquals(
            ExtractionDecision.FULL,
            decideExtraction(
                filled = 1000,
                total = total,
                existingFingerprint = "1000|\"old-etag\"",
                existingComplete = true,
                currentFingerprint = fingerprint,
            ),
        )
    }

    @Test
    fun `uses the head read when the server did not report a length`() {
        assertEquals(
            ExtractionDecision.SKIP,
            decideExtraction(
                filled = 0,
                total = 0,
                existingFingerprint = null,
                existingComplete = false,
                currentFingerprint = "0|",
            ),
        )
        assertEquals(
            ExtractionDecision.PARTIAL,
            decideExtraction(
                filled = 4096,
                total = 0,
                existingFingerprint = null,
                existingComplete = false,
                currentFingerprint = "0|",
            ),
        )
    }
}
