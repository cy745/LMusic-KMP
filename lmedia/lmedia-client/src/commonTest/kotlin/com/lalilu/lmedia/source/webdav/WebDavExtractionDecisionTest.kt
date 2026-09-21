package com.lalilu.lmedia.source.webdav

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 提取决策矩阵。
 *
 * 两个节点（头部窗口 / 100%）的取舍直接决定"听歌时会不会多读一次标签"以及"时长与封面能不能拿到"，
 * 所以这里把每条边界都钉住。
 *
 * 实测依据（本机 7 首 FLAC）：标签与内嵌封面的尾部偏移在 0.18–0.83 MB，中位 0.26 MB，
 * 因此头部窗口取 1 MB；兜底窗口 4 MB 用于"封面被窗口切断"这类读不出结果的情况。
 */
class WebDavExtractionDecisionTest {

    /** 30MB 的典型整曲大小：窗口远小于文件，才能看出"固定窗口"与"百分比"的区别。 */
    private val total = 30L * 1024 * 1024
    private val fingerprint = "$total|\"etag\""

    private fun decide(
        filled: Long,
        existingFingerprint: String? = null,
        existingComplete: Boolean = false,
        existingExaminedBytes: Long = 0L,
        currentFingerprint: String = fingerprint,
        totalSize: Long = total,
    ) = decideExtraction(
        filled = filled,
        total = totalSize,
        existingFingerprint = existingFingerprint,
        existingComplete = existingComplete,
        currentFingerprint = currentFingerprint,
        existingExaminedBytes = existingExaminedBytes,
    )

    @Test
    fun `waits until the head window is cached`() {
        assertEquals(ExtractionDecision.SKIP, decide(filled = HEAD_WINDOW_BYTES - 1))
    }

    @Test
    fun `reads the tags as soon as the head window is complete`() {
        assertEquals(ExtractionDecision.PARTIAL, decide(filled = HEAD_WINDOW_BYTES))
    }

    @Test
    fun `head window is a fixed size and does not scale with the file`() {
        // 45MB 与 17MB 的文件触发点一样（都只需要 1MB），而不是各自 30%
        assertEquals(
            ExtractionDecision.PARTIAL,
            decide(filled = HEAD_WINDOW_BYTES, totalSize = 45L * 1024 * 1024),
            "case A: 45MB 文件在 1MB 处部分提取",
        )
        assertEquals(
            ExtractionDecision.PARTIAL,
            decide(filled = HEAD_WINDOW_BYTES, totalSize = 17L * 1024 * 1024),
            "case B: 17MB 文件在 1MB 处部分提取",
        )
        // 已读过头部窗口、缓存涨到 2MB（还没到兜底窗口）时不该重复读
        assertEquals(
            ExtractionDecision.SKIP,
            decide(
                filled = 2L * 1024 * 1024,
                existingFingerprint = fingerprint,
                existingExaminedBytes = HEAD_WINDOW_BYTES,
            ),
            "case C: 30MB 文件 2MB 处不重复读",
        )
        val big = 45L * 1024 * 1024
        val bigFingerprint = "$big|\"etag\""
        assertEquals(
            ExtractionDecision.SKIP,
            decide(
                filled = 2L * 1024 * 1024,
                totalSize = big,
                existingFingerprint = bigFingerprint,
                existingExaminedBytes = HEAD_WINDOW_BYTES,
                currentFingerprint = bigFingerprint,
            ),
            "case D: 45MB 文件 2MB 处不重复读",
        )
    }

    @Test
    fun `waits for the whole file when it is smaller than the head window`() {
        val small = 512L * 1024
        assertEquals(
            ExtractionDecision.SKIP,
            decide(filled = small - 1, totalSize = small),
            "小于窗口的文件在下载完成前不读标签",
        )
        assertEquals(
            ExtractionDecision.FULL,
            decide(filled = small, totalSize = small),
            "下载完成即完整提取，不做部分提取",
        )
    }

    @Test
    fun `fully extracts once the file is complete`() {
        assertEquals(ExtractionDecision.FULL, decide(filled = total))
    }

    @Test
    fun `upgrades a partial record to a full one at completion`() {
        assertEquals(
            ExtractionDecision.FULL,
            decide(
                filled = total,
                existingFingerprint = fingerprint,
                existingComplete = false,
                existingExaminedBytes = HEAD_WINDOW_BYTES,
            ),
        )
    }

    @Test
    fun `skips when the complete record already matches the fingerprint`() {
        assertEquals(
            ExtractionDecision.SKIP,
            decide(filled = total, existingFingerprint = fingerprint, existingComplete = true),
        )
        assertEquals(
            ExtractionDecision.SKIP,
            decide(
                filled = HEAD_WINDOW_BYTES,
                existingFingerprint = fingerprint,
                existingComplete = false,
                existingExaminedBytes = HEAD_WINDOW_BYTES,
            ),
            "头部窗口已经读过且没有新字节，不重复读",
        )
    }

    @Test
    fun `grows the window once when the head read produced nothing usable`() {
        // 头部窗口读过（1MB）但没读出结果：缓存涨到兜底窗口时再试一次
        assertEquals(
            ExtractionDecision.PARTIAL,
            decide(
                filled = HEAD_WINDOW_FALLBACK_BYTES,
                existingFingerprint = fingerprint,
                existingComplete = false,
                existingExaminedBytes = HEAD_WINDOW_BYTES,
            ),
        )
        assertEquals(
            ExtractionDecision.SKIP,
            decide(
                filled = HEAD_WINDOW_FALLBACK_BYTES - 1,
                existingFingerprint = fingerprint,
                existingComplete = false,
                existingExaminedBytes = HEAD_WINDOW_BYTES,
            ),
            "还没到兜底窗口就不重试",
        )
        assertEquals(
            ExtractionDecision.SKIP,
            decide(
                filled = 8L * 1024 * 1024,
                existingFingerprint = fingerprint,
                existingComplete = false,
                existingExaminedBytes = HEAD_WINDOW_FALLBACK_BYTES,
            ),
            "兜底窗口只扩大一次，之后等整首",
        )
    }

    @Test
    fun `re-extracts when the remote file changed`() {
        assertEquals(
            ExtractionDecision.PARTIAL,
            decide(
                filled = HEAD_WINDOW_BYTES,
                existingFingerprint = "$total|\"old-etag\"",
                existingComplete = true,
                existingExaminedBytes = total,
            ),
        )
        assertEquals(
            ExtractionDecision.FULL,
            decide(
                filled = total,
                existingFingerprint = "$total|\"old-etag\"",
                existingComplete = true,
            ),
        )
    }

    @Test
    fun `uses the head read when the server did not report a length`() {
        assertEquals(ExtractionDecision.SKIP, decide(filled = 0, totalSize = 0))
        assertEquals(ExtractionDecision.PARTIAL, decide(filled = 4096, totalSize = 0))
    }
}
