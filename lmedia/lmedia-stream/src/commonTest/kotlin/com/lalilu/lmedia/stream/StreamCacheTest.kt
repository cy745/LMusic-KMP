package com.lalilu.lmedia.stream

import kotlin.random.Random
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 缓存账目、淘汰与区间覆盖的覆盖测试。
 *
 * 这里验证的是"容量上限下的取舍"——配额边界、LRU 顺序、部分缓存优先、正在播放的不动、
 * 账目跨实例保留、以及远端文件变小后的重建；外加分段的可见性与覆盖计算。真实字节流由
 * [StreamProxyTest] 覆盖。
 */
class StreamCacheTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var now = 1_000L

    private fun newCache(root: String, segmentSize: Long = StreamCache.SEGMENT_SIZE): StreamCache =
        StreamCache(
            cacheRoot = root,
            namespace = "test",
            json = json,
            clock = { now },
            segmentSize = segmentSize,
        ).also { it.ensureReady() }

    /** 每个用例一个全新目录：账目文件会跨进程保留，共用目录会让断言互相污染。 */
    private fun freshRoot(name: String): String = "build/test-stream-cache/$name-${Random.nextLong()}"

    /** 小分段：几十个字节就能覆盖"跨分段""分段越界"这些边界。 */
    private fun newSegmentedCache(name: String): StreamCache =
        newCache(freshRoot(name), segmentSize = 100L)

    private fun StreamCache.putSegment(key: String, index: Int, size: Int): Boolean {
        val writer = openSegmentWriter(key, index, size.toLong())
        writer.write(ByteArray(size) { (it % 251).toByte() })
        return writer.commit()
    }

    private fun StreamCache.putComplete(key: String, size: Int, total: Long = size.toLong()) {
        appendBytes(key, ByteArray(size))
        touch(key, total, at = now)
    }

    @Test
    fun `keeps everything while under the quota`() {
        val cache = newCache(freshRoot("under"))
        cache.putComplete("a", 100)
        cache.putComplete("b", 100)

        assertEquals(emptyList(), cache.evictIfNeeded(quotaBytes = 1_000))
        assertEquals(200L, cache.usedBytes())
        assertEquals(setOf("a", "b"), cache.keys().toSet())
    }

    @Test
    fun `treats zero or negative quota as unlimited`() {
        val cache = newCache(freshRoot("unlimited"))
        cache.putComplete("a", 500)

        assertEquals(emptyList(), cache.evictIfNeeded(quotaBytes = 0))
        assertEquals(emptyList(), cache.evictIfNeeded(quotaBytes = -1))
        assertEquals(1, cache.keys().size)
    }

    @Test
    fun `evicts least recently used entries first`() {
        val cache = newCache(freshRoot("lru"))
        cache.putComplete("old", 400)
        now += 1_000
        cache.putComplete("middle", 400)
        now += 1_000
        cache.putComplete("new", 400)
        // 再听一次 old：它变成最近使用过的
        now += 1_000
        cache.touch("old", at = now)

        // 配额 900 → 需要腾出至少 300（并把占用压到 90% 即 810）
        val evicted = cache.evictIfNeeded(quotaBytes = 900)

        assertEquals(listOf("middle"), evicted, "最久未使用的应先被淘汰")
        assertEquals(setOf("old", "new"), cache.keys().toSet())
    }

    @Test
    fun `evicts partial entries before complete ones even if they are newer`() {
        val cache = newCache(freshRoot("partial-first"))
        // complete 最久没用过；partial 刚听过
        cache.putComplete("complete-old", 400, total = 400)
        now += 10_000
        cache.appendBytes("partial-new", ByteArray(400)) // 没有 totalSize → 视为部分缓存
        cache.touch("partial-new", at = now)

        val evicted = cache.evictIfNeeded(quotaBytes = 700)

        assertEquals(
            listOf("partial-new"),
            evicted,
            "部分缓存只是听过一小段的副产品，重下代价最小，应优先淘汰",
        )
        assertEquals(listOf("complete-old"), cache.keys())
    }

    @Test
    fun `never evicts the key that is currently playing`() {
        val cache = newCache(freshRoot("pinned"))
        cache.putComplete("playing", 400)
        now += 1_000
        cache.putComplete("other", 400)

        val evicted = cache.evictIfNeeded(quotaBytes = 500, pinned = setOf("playing"))

        assertEquals(listOf("other"), evicted, "可淘汰的只剩 other")
        assertEquals(listOf("playing"), cache.keys(), "正在播放的文件必须留下")
    }

    @Test
    fun `reports entry completeness only when the remote length is known`() {
        val cache = newCache(freshRoot("entries"))
        cache.appendBytes("partial", ByteArray(100))
        cache.touch("partial", totalSize = 1_000, at = now)
        cache.putComplete("done", 100, total = 100)

        val entries = cache.entries().associateBy { it.key }

        assertFalse(entries.getValue("partial").isComplete, "100/1000 只是部分缓存")
        assertEquals(100L, entries.getValue("partial").bytes)
        assertEquals(1_000L, entries.getValue("partial").totalSize)
        assertTrue(entries.getValue("done").isComplete)
        assertEquals(now, entries.getValue("done").usedAt)
    }

    @Test
    fun `keeps the usage record across cache instances`() {
        val root = freshRoot("persist")
        val first = newCache(root)
        first.putComplete("a", 100, total = 100)
        first.persistUsage()

        // 新实例（模拟重启）：LRU 顺序必须保留，否则老文件会被当成"刚用过"
        now += 60_000
        val second = newCache(root)
        second.putComplete("b", 100, total = 100)

        val evicted = second.evictIfNeeded(quotaBytes = 150)
        assertEquals(listOf("a"), evicted, "重启后仍应知道 a 更久没用过")
    }

    @Test
    fun `rebuilds the cache when the remote file became smaller`() {
        val cache = newCache(freshRoot("rebuild"))
        cache.putComplete("replaced", 1_000, total = 1_000)

        // 远端换成了更小的文件：旧前缀比新文件还长，必须丢弃
        assertFalse(cache.ensureConsistent("replaced", expectedTotal = 400))
        assertEquals(0L, cache.prefixSize("replaced"))
        assertNull(cache.localPath("replaced"))

        // 长度一致或未知时不动缓存
        cache.putComplete("same", 500, total = 500)
        assertTrue(cache.ensureConsistent("same", expectedTotal = 500))
        assertEquals(500L, cache.prefixSize("same"))
        assertTrue(cache.ensureConsistent("same", expectedTotal = 0))
        assertEquals(500L, cache.prefixSize("same"))
    }

    @Test
    fun `eviction cleans up both the file and its usage record`() {
        val root = freshRoot("cleanup")
        val cache = newCache(root)
        cache.putComplete("gone", 500, total = 500)

        val evicted = cache.evictIfNeeded(quotaBytes = 100)
        assertEquals(listOf("gone"), evicted)
        assertFalse("gone" in cache.keys())
        assertEquals(0L, cache.usedBytes())
        assertEquals(0L, cache.prefixSize("gone"))

        // 账目也要清掉：否则下次复用它算出的占用会虚高
        cache.persistUsage()
        val reopened = newCache(root)
        assertEquals(0L, reopened.usedBytes())
    }

    @Test
    fun `keeps serving the cached prefix after a normal append`() {
        val cache = newCache(freshRoot("prefix"))
        cache.appendBytes("k", ByteArray(10) { it.toByte() })

        assertEquals(10L, cache.prefixSize("k"))
        assertContentEquals(ByteArray(10) { it.toByte() }, cache.readBytes("k", 0, 10))
        assertContentEquals(ByteArray(4) { (it + 6).toByte() }, cache.readBytes("k", 6, 4))
    }

    // ── 分段与区间覆盖 ──

    @Test
    fun `segments live outside the prefix and show up in coverage`() {
        val cache = newSegmentedCache("segments")
        cache.appendBytes("k", ByteArray(50))
        assertTrue(cache.putSegment("k", 2, 100), "分段应写入成功")

        assertEquals(50L, cache.prefixSize("k"))
        assertEquals(listOf(CachedRange(0, 49), CachedRange(200, 299)), cache.coveredRanges("k"))
        assertEquals(150L, cache.coveredBytes("k"))
        assertEquals(mapOf(2 to 100L), cache.segments("k"))
    }

    @Test
    fun `a segment is only visible after commit`() {
        val cache = newSegmentedCache("commit")
        val writer = cache.openSegmentWriter("k", 1, 100)
        writer.write(ByteArray(100))

        // 还没 commit：临时文件不算覆盖，否则崩溃后会把半段当成完整段
        assertEquals(emptyMap(), cache.segments("k"))
        assertEquals(emptyList(), cache.coveredRanges("k"))

        assertTrue(writer.commit())
        assertEquals(setOf(1), cache.segments("k").keys)
        assertEquals(listOf(CachedRange(100, 199)), cache.coveredRanges("k"))
    }

    @Test
    fun `an aborted segment leaves nothing behind`() {
        val cache = newSegmentedCache("abort")
        val writer = cache.openSegmentWriter("k", 1, 100)
        writer.write(ByteArray(100))
        writer.abort()

        assertEquals(emptyMap(), cache.segments("k"))
        assertEquals(0L, cache.coveredBytes("k"))
    }

    @Test
    fun `a short trailing segment covers only the bytes it has`() {
        val cache = newSegmentedCache("short-tail")
        // 总长 250：第 2 段只有 50 字节
        cache.putSegment("k", 2, 50)

        assertEquals(listOf(CachedRange(200, 249)), cache.coveredRanges("k"))
        assertEquals(listOf(CachedRange(200, 249)), cache.coveredRanges("k", totalSize = 250))
        assertEquals(emptyList(), cache.coveredRanges("k", totalSize = 200), "越界分段不算覆盖")
    }

    @Test
    fun `reads a span from whichever store holds it`() {
        val cache = newSegmentedCache("span")
        cache.appendBytes("k", ByteArray(50) { 9 })
        cache.putSegment("k", 2, 100)

        val fromPrefix = cache.openSpan("k", 10, 1000)!!
        assertEquals(50L - 10, fromPrefix.length, "前缀里只能读到前缀末尾")
        fromPrefix.source.use { it.readByteArray(40) }

        val fromSegment = cache.openSpan("k", 250, 1000)!!
        assertEquals(50L, fromSegment.length)
        val bytes = fromSegment.source.use { it.readByteArray(50) }
        // 分段内容按段内偏移生成：第 2 段第 50 字节起就是 50、51、…
        assertContentEquals(ByteArray(50) { (50 + it).toByte() }, bytes)

        assertNull(cache.openSpan("k", 100, 1000), "空洞里没有连续可读的一段")
        assertNull(cache.openSpan("k", 300, 1000), "越过前缀与分段就没了")
        assertEquals(20L, cache.openSpan("k", 280, 20)!!.also { it.source.close() }.length)
    }

    @Test
    fun `reads bytes from a segment through readBytes`() {
        val cache = newSegmentedCache("read-bytes")
        cache.putSegment("k", 1, 100)

        assertContentEquals(
            ByteArray(10) { (5 + it).toByte() },
            cache.readBytes("k", 105, 10),
        )
        assertNull(cache.readBytes("k", 0, 10), "前缀没有内容时不该从别处凑")
    }

    @Test
    fun `a key that only has segments is still a cache key`() {
        val cache = newSegmentedCache("segment-only")
        cache.putSegment("k", 0, 100)

        assertEquals(listOf("k"), cache.keys())
        assertEquals(100L, cache.usedBytes(), "占用统计必须算上分段")
    }

    @Test
    fun `deleting a key removes its segments too`() {
        val cache = newSegmentedCache("delete-segments")
        cache.appendBytes("k", ByteArray(50))
        cache.putSegment("k", 2, 100)

        cache.delete("k")

        assertEquals(emptyList(), cache.keys())
        assertEquals(emptyMap(), cache.segments("k"))
        assertEquals(0L, cache.coveredBytes("k"))
    }

    @Test
    fun `segments covered by the prefix stop occupying space`() {
        val cache = newSegmentedCache("overlap")
        cache.putSegment("k", 0, 100)
        cache.putSegment("k", 1, 100)
        cache.putSegment("k", 2, 100)
        // 前缀长到 250：第 0、1 段整段落在前缀里，第 2 段只压了一半
        cache.appendBytes("k", ByteArray(250))

        cache.discardSegmentsWithinPrefix("k")

        assertEquals(setOf(2), cache.segments("k").keys)
        assertEquals(listOf(CachedRange(0, 299)), cache.coveredRanges("k"), "覆盖仍然是连续的")
        assertEquals(300L, cache.coveredBytes("k"))
    }

    @Test
    fun `shrinking the remote file drops the segments beyond its new length`() {
        val cache = newSegmentedCache("shrink")
        cache.putSegment("k", 2, 100)
        cache.putSegment("k", 3, 100)

        assertTrue(cache.ensureConsistent("k", expectedTotal = 250))

        assertEquals(setOf(2), cache.segments("k").keys, "起点越过新长度的分段必须清掉")
    }

    @Test
    fun `missing ranges describe exactly the holes to fetch`() {
        val cache = newSegmentedCache("holes")
        cache.appendBytes("k", ByteArray(100))
        cache.putSegment("k", 2, 100)

        assertEquals(
            listOf(CachedRange(100, 199)),
            cache.missingRanges("k", from = 0, toInclusive = 299),
        )
        assertEquals(emptyList(), cache.missingRanges("k", from = 200, toInclusive = 299))
    }
}
