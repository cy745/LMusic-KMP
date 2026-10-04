package com.lalilu.lmedia.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 区间覆盖模型的覆盖测试。
 *
 * 这是 ③ 的地基：缓存能不能"记住哪段有、哪段没有"全在这里。`normalizeCoverage` 与
 * `missingCoverage` 的边界（相邻、重叠、乱序、跨多个空洞、越界）逐条钉死。
 */
class StreamCoverageTest {

    @Test
    fun `normalizes unsorted and overlapping ranges`() {
        assertEquals(
            listOf(CachedRange(0, 199)),
            normalizeCoverage(
                listOf(
                    CachedRange(100, 199),
                    CachedRange(0, 50),
                    CachedRange(50, 120),
                ),
            ),
        )
    }

    @Test
    fun `merges adjacent ranges because reading them is continuous`() {
        assertEquals(
            listOf(CachedRange(0, 19)),
            normalizeCoverage(listOf(CachedRange(0, 9), CachedRange(10, 19))),
        )
    }

    @Test
    fun `keeps a real hole as a real hole`() {
        assertEquals(
            listOf(CachedRange(0, 9), CachedRange(20, 29)),
            normalizeCoverage(listOf(CachedRange(20, 29), CachedRange(0, 9))),
        )
    }

    @Test
    fun `normalizing nothing yields nothing`() {
        assertEquals(emptyList(), normalizeCoverage(emptyList()))
    }

    @Test
    fun `reports nothing missing inside a fully covered span`() {
        val covered = listOf(CachedRange(0, 999))

        assertEquals(emptyList(), missingCoverage(covered, 100, 199))
        assertTrue(isCoverageComplete(covered, 0, 999))
    }

    @Test
    fun `reports the holes before and after covered spans`() {
        val covered = listOf(CachedRange(100, 199), CachedRange(400, 499))

        assertEquals(
            listOf(CachedRange(0, 99), CachedRange(200, 399), CachedRange(500, 800)),
            missingCoverage(covered, 0, 800),
        )
    }

    @Test
    fun `clips missing spans to the requested window`() {
        val covered = listOf(CachedRange(100, 199))

        // 左边界裁剪：只报窗口内那一段，不越界到 0 之前
        assertEquals(listOf(CachedRange(50, 99)), missingCoverage(covered, 50, 150))
        // 右边界裁剪：窗口在覆盖区间内结束时不再补尾巴
        assertEquals(emptyList(), missingCoverage(covered, 150, 180))
        // 窗口跨出覆盖区间右端
        assertEquals(listOf(CachedRange(200, 250)), missingCoverage(covered, 150, 250))
    }

    @Test
    fun `treats an empty window as nothing missing`() {
        assertEquals(emptyList(), missingCoverage(emptyList(), 10, 9))
    }

    @Test
    fun `finds the contiguous run starting at a position`() {
        val covered = listOf(CachedRange(0, 199), CachedRange(400, 499))

        assertEquals(CachedRange(0, 199), contiguousCoverage(covered, 0, 1000))
        assertEquals(CachedRange(150, 199), contiguousCoverage(covered, 150, 1000))
        assertEquals(CachedRange(400, 450), contiguousCoverage(covered, 400, 51))
        assertNull(contiguousCoverage(covered, 200, 1000), "空洞起点没有缓存")
        assertNull(contiguousCoverage(covered, 500, 1000), "越过后缀区间末尾")
        assertNull(contiguousCoverage(covered, 0, 0), "长度为 0 不算可读")
    }

    @Test
    fun `completeness requires a single range to span the whole window`() {
        assertFalse(isCoverageComplete(listOf(CachedRange(0, 99)), 0, 199))
        assertTrue(isCoverageComplete(listOf(CachedRange(0, 199)), 0, 199))
        assertTrue(isCoverageComplete(emptyList(), 10, 9), "空窗口视为已完整")
        // 入参约定为 normalizeCoverage 后的列表：相邻两段在那里已经合成一段
        assertFalse(isCoverageComplete(listOf(CachedRange(0, 99), CachedRange(100, 199)), 0, 199))
    }

    @Test
    fun `counts covered bytes across ranges`() {
        assertEquals(0L, coverageBytes(emptyList()))
        assertEquals(300L, coverageBytes(listOf(CachedRange(0, 99), CachedRange(400, 599))))
    }
}
