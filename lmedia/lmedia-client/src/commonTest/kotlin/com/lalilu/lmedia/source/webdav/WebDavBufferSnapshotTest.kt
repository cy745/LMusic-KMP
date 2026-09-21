package com.lalilu.lmedia.source.webdav

import com.lalilu.lmedia.domain.source.BufferedRange
import com.lalilu.lmedia.stream.CachedRange
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 缓存覆盖 → 进度条比例区间的换算。
 *
 * 这里钉住两件事：区间本身原样传递（跳转后的碎片化必须画成多段），以及"末字节 + 1"那一位
 * ——少加这一位会让整首缓存显示成 99.99%，进度条永远差一点。
 */
class WebDavBufferSnapshotTest {

    private fun snapshot(covered: List<CachedRange>, total: Long) =
        WebDavBufferSnapshot(key = "k", covered = covered, total = total)

    @Test
    fun fullCoverageBecomesOneRangeEndingAtOne() {
        val ranges = snapshot(listOf(CachedRange(0, 999)), total = 1_000).ranges

        assertEquals(listOf(BufferedRange(0f, 1f)), ranges)
    }

    @Test
    fun fragmentedCoverageKeepsBothParts() {
        val ranges = snapshot(
            covered = listOf(CachedRange(0, 249), CachedRange(750, 999)),
            total = 1_000,
        ).ranges

        assertEquals(
            listOf(BufferedRange(0f, 0.25f), BufferedRange(0.75f, 1f)),
            ranges,
        )
    }

    @Test
    fun unknownTotalLengthReportsNothing() {
        assertEquals(emptyList(), snapshot(listOf(CachedRange(0, 99)), total = 0L).ranges)
        assertEquals(emptyList(), snapshot(emptyList(), total = 1_000).ranges)
    }

    @Test
    fun coverageBeyondTheDeclaredLengthIsClamped() {
        // 远端文件变小后分段里可能留着旧字节，换算时不能越过 1f
        val ranges = snapshot(listOf(CachedRange(0, 1_999)), total = 1_000).ranges

        assertEquals(listOf(BufferedRange(0f, 1f)), ranges)
    }
}
