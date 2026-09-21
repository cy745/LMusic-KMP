package com.lalilu.lmedia.source.webdav

import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 缓存账目与淘汰的覆盖测试。
 *
 * 这里验证的是"容量上限下的取舍"——配额边界、LRU 顺序、部分缓存优先、正在播放的不动、
 * 账目跨实例保留、以及远端文件变小后的重建。真实字节流由 [WebDavProxyTest] 覆盖。
 */
class WebDavCacheTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var now = 1_000L

    private fun newCache(root: String): WebDavCache =
        WebDavCache(cacheRoot = root, json = json, clock = { now }).also { it.ensureReady() }

    /** 每个用例一个全新目录：账目文件会跨进程保留，共用目录会让断言互相污染。 */
    private fun freshRoot(name: String): String = "build/test-webdav-cache/$name-${Random.nextLong()}"

    private fun WebDavCache.putComplete(key: String, size: Int, total: Long = size.toLong()) {
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
        assertEquals(0L, cache.filledSize("replaced"))
        assertNull(cache.localPath("replaced"))

        // 长度一致或未知时不动缓存
        cache.putComplete("same", 500, total = 500)
        assertTrue(cache.ensureConsistent("same", expectedTotal = 500))
        assertEquals(500L, cache.filledSize("same"))
        assertTrue(cache.ensureConsistent("same", expectedTotal = 0))
        assertEquals(500L, cache.filledSize("same"))
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
        assertEquals(0L, cache.filledSize("gone"))

        // 账目也要清掉：否则下次复用它算出的占用会虚高
        cache.persistUsage()
        val reopened = newCache(root)
        assertEquals(0L, reopened.usedBytes())
    }

    @Test
    fun `keeps serving the cached prefix after a normal append`() {
        val cache = newCache(freshRoot("prefix"))
        cache.appendBytes("k", ByteArray(10) { it.toByte() })

        assertEquals(10L, cache.filledSize("k"))
        assertContentEquals(ByteArray(10) { it.toByte() }, cache.readBytes("k", 0, 10))
        assertContentEquals(ByteArray(4) { (it + 6).toByte() }, cache.readBytes("k", 6, 4))
    }
}
