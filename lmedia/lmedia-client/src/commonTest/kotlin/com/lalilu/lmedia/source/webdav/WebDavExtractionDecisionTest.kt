package com.lalilu.lmedia.source.webdav

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 提取节点与决策矩阵。
 *
 * 时机直接决定"听歌时多久能看到标题与封面"以及"会不会重复读标签"，所以把每条边界都钉住：
 * 1MB 前缀先试一次 → 30% 只在上一次没读到东西时补一次 → 完成节点补时长。
 */
class WebDavExtractionDecisionTest {

    private val fingerprint = "1000|\"etag\""
    private val head = HEAD_EXTRACTION_BYTES
    private val total = 40L * 1024 * 1024 // 40MB：三个节点都会经过

    private fun record(
        fingerprint: String = this.fingerprint,
        complete: Boolean = false,
        title: String = "",
        hasCover: Boolean = false,
    ) = WebDavMetadataRecord(
        fingerprint = fingerprint,
        complete = complete,
        extractedAt = 1,
        title = title,
        hasCover = hasCover,
    )

    @Test
    fun `node schedule goes from one megabyte to thirty percent to completion`() {
        assertEquals(null, extractionNodeOf(filled = head - 1, total = total))
        assertEquals(ExtractionNode.PREFIX_HEAD, extractionNodeOf(filled = head, total = total))
        assertEquals(
            ExtractionNode.PARTIAL,
            extractionNodeOf(filled = (total * 0.3).toLong(), total = total),
        )
        assertEquals(ExtractionNode.FULL, extractionNodeOf(filled = total, total = total))
    }

    @Test
    fun `small files skip the head node and go straight to partial`() {
        val small = 2L * 1024 * 1024 // 2MB：30% = 600KB，比 1MB 更早到
        assertEquals(ExtractionNode.PARTIAL, extractionNodeOf(filled = 700_000, total = small))
        assertEquals(ExtractionNode.FULL, extractionNodeOf(filled = small, total = small))
    }

    @Test
    fun `first attempt happens as soon as one megabyte is cached`() {
        assertEquals(
            ExtractionDecision.PARTIAL,
            decideExtraction(ExtractionNode.PREFIX_HEAD, existing = null, currentFingerprint = fingerprint),
        )
    }

    @Test
    fun `thirty percent is skipped when the head read already got metadata`() {
        val partial = record(title = "Friend", hasCover = true)
        assertEquals(
            ExtractionDecision.SKIP,
            decideExtraction(ExtractionNode.PARTIAL, partial, fingerprint, ExtractionNode.PREFIX_HEAD),
        )
    }

    @Test
    fun `thirty percent retries when the head read found nothing`() {
        val empty = record() // 1MB 时什么都没读出来
        assertEquals(
            ExtractionDecision.PARTIAL,
            decideExtraction(ExtractionNode.PARTIAL, empty, fingerprint, ExtractionNode.PREFIX_HEAD),
        )
    }

    @Test
    fun `completion always upgrades an incomplete record`() {
        val partial = record(title = "Friend", hasCover = true)
        assertEquals(
            ExtractionDecision.FULL,
            decideExtraction(ExtractionNode.FULL, partial, fingerprint, ExtractionNode.PARTIAL),
        )
        assertEquals(
            ExtractionDecision.SKIP,
            decideExtraction(ExtractionNode.FULL, partial.copy(complete = true), fingerprint, ExtractionNode.PARTIAL),
        )
    }

    @Test
    fun `same node is never parsed twice`() {
        assertEquals(
            ExtractionDecision.SKIP,
            decideExtraction(ExtractionNode.PREFIX_HEAD, null, fingerprint, ExtractionNode.PREFIX_HEAD),
        )
        assertEquals(
            ExtractionDecision.SKIP,
            decideExtraction(ExtractionNode.PARTIAL, null, fingerprint, ExtractionNode.PARTIAL),
        )
    }

    @Test
    fun `a replaced remote file restarts the whole schedule`() {
        val staleRecord = record(fingerprint = "1000|\"old-etag\"", complete = true, title = "Old")
        assertEquals(
            ExtractionDecision.PARTIAL,
            decideExtraction(ExtractionNode.PREFIX_HEAD, staleRecord, fingerprint),
        )
        assertEquals(
            ExtractionDecision.FULL,
            decideExtraction(ExtractionNode.FULL, staleRecord, fingerprint),
        )
    }

    @Test
    fun `uses the head read when the server did not report a length`() {
        assertEquals(null, extractionNodeOf(filled = 0, total = 0))
        assertEquals(ExtractionNode.PREFIX_HEAD, extractionNodeOf(filled = 4096, total = 0))
        assertEquals(
            ExtractionDecision.PARTIAL,
            decideExtraction(ExtractionNode.PREFIX_HEAD, existing = null, currentFingerprint = "0|"),
        )
    }
}
