package com.lalilu.lmedia.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 优先级调度的覆盖测试：播放头最高、其后顺序铺满、跳过的空洞最低。
 *
 * 用 1000 字节总长与 100 字节前瞻窗口，边界一眼能算清。注意这一档只报**窗口内**的空洞——
 * 窗口外的属于下一档。
 */
class StreamFillPlanTest {

    private val total = 1_000L
    private val lookahead = 100L

    private fun next(
        coverage: List<CachedRange>,
        playhead: Long,
        allowBackground: Boolean = true,
    ): CachedRange? = nextFillRange(
        coverage = coverage,
        totalSize = total,
        playhead = playhead,
        lookaheadBytes = lookahead,
        allowBackground = allowBackground,
    )

    @Test
    fun `fills from the playhead when nothing is cached`() {
        assertEquals(CachedRange(0, 99), next(emptyList(), playhead = 0))
    }

    @Test
    fun `prefers the hole at the playhead over the skipped hinterland`() {
        // 播放头 400：前面 300..399 被跳过，而 400..499 也没缓存
        val coverage = listOf(CachedRange(0, 299), CachedRange(500, 999))

        assertEquals(
            CachedRange(400, 499),
            next(coverage, playhead = 400),
            "马上要播到的 400..499 必须排在跳过的 300..399 前面",
        )
    }

    @Test
    fun `keeps filling the lookahead window while it still has holes`() {
        val coverage = listOf(CachedRange(0, 519), CachedRange(700, 999))

        // 播放头 500 的前瞻窗口是 500..599，缺 520..599
        assertEquals(CachedRange(520, 599), next(coverage, playhead = 500))
    }

    @Test
    fun `moves on to sequential fill once the lookahead window is covered`() {
        val coverage = listOf(CachedRange(0, 599))

        assertEquals(CachedRange(600, 999), next(coverage, playhead = 500))
    }

    @Test
    fun `treats the skipped hinterland as the last resort`() {
        // 播放头及其后方都已缓存，只剩播放器跳过的 0..399
        val coverage = listOf(CachedRange(400, 999))

        assertEquals(CachedRange(0, 399), next(coverage, playhead = 500))
    }

    @Test
    fun `nothing left to fill once everything is covered`() {
        assertNull(next(listOf(CachedRange(0, 999)), playhead = 500))
    }

    @Test
    fun `metered network only fills the playhead window`() {
        // 前瞻窗口还缺 → 照补
        val holeAtPlayhead = listOf(CachedRange(0, 499), CachedRange(600, 999))
        assertEquals(
            CachedRange(500, 599),
            next(holeAtPlayhead, playhead = 500, allowBackground = false),
        )

        // 前瞻窗口已满 → 停下：既不铺满后面，也不回头补跳过的
        val coveredAhead = listOf(CachedRange(400, 999))
        assertNull(next(coveredAhead, playhead = 500, allowBackground = false))
    }

    @Test
    fun `clamps the playhead into the file`() {
        // 播放头越界时裁到末尾；这一档只报窗口内的空洞，取整段由抓取方按分段粒度扩展
        assertEquals(CachedRange(999, 999), next(emptyList(), playhead = 5_000))
        assertEquals(CachedRange(0, 99), next(emptyList(), playhead = -5))
    }

    @Test
    fun `zero lookahead means no window at all`() {
        // 窗口为 0：第 1 档不做，直接从播放头往后铺
        assertEquals(
            CachedRange(400, 999),
            nextFillRange(emptyList(), total, playhead = 400, lookaheadBytes = 0L, allowBackground = true),
        )
        // 计费网络下就无事可做了（播放器自己的请求仍然照常服务）
        assertNull(
            nextFillRange(emptyList(), total, playhead = 400, lookaheadBytes = 0L, allowBackground = false),
        )
    }

    @Test
    fun `unknown total length has nothing to plan`() {
        assertNull(
            nextFillRange(
                coverage = emptyList(),
                totalSize = 0L,
                playhead = 0L,
                lookaheadBytes = lookahead,
                allowBackground = true,
            ),
        )
    }
}
