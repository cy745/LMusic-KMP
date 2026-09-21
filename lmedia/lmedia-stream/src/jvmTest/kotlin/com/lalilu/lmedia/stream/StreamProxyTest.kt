package com.lalilu.lmedia.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 回环代理的端到端用例：真的起一个 HTTP 服务，用真的 HTTP 客户端按播放器的方式发请求，
 * 断言状态码、`Content-Range` 与字节内容——代理最容易写错的地方（区间截取、缓存复用、
 * 416、多区间退回整文件）都在这里。
 */
class StreamProxyTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val payload = ByteArray(1000) { index -> (index % 251).toByte() }
    private lateinit var proxy: StreamProxy
    private lateinit var backend: FakeBackend
    private lateinit var cache: StreamCache

    @AfterTest
    fun tearDown() = runTest {
        if (::proxy.isInitialized) proxy.stop()
    }

    /**
     * 默认关掉后台补齐、前瞻窗口设为 0：这些用例断言的是"播放器请求引起的取数"，
     * 后台 worker 的存在会让 `backend.fetched` 变成不确定的。
     */
    private suspend fun startProxy(
        payload: ByteArray = this.payload,
        segmentSize: Long = StreamCache.SEGMENT_SIZE,
        lookaheadBytes: Long = 0L,
        allowBackgroundFill: () -> Boolean = { false },
        quotaBytes: () -> Long = { 0L },
        evictionIntervalMillis: Long = StreamProxy.EVICTION_INTERVAL_MILLIS,
    ): String {
        backend = FakeBackend(payload)
        val cacheRoot = Files.createTempDirectory("lmusic-stream-proxy").toString()
        cache = StreamCache(cacheRoot, "test", json, segmentSize = segmentSize)
        proxy = StreamProxy(
            cache = cache,
            backend = backend,
            quotaBytes = quotaBytes,
            evictionIntervalMillis = evictionIntervalMillis,
            allowBackgroundFill = allowBackgroundFill,
            lookaheadBytes = lookaheadBytes,
        )
        return proxy.start()
    }

    /** 后台 worker 是真并发的：等它把活干完，而不是猜一个 sleep。 */
    private fun awaitCondition(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("等待条件超时（${timeoutMillis}ms）")
    }

    @Test
    fun `serves the probe range and then reuses the cache for later requests`() = runTest {
        val base = startProxy()

        // AVPlayer 会先发 2 字节探测
        val probe = get("$base/audio/testkey", "bytes=0-1")
        assertEquals(206, probe.status)
        assertEquals("bytes 0-1/1000", probe.contentRange)
        assertContentEquals(payload.copyOfRange(0, 2), probe.body)
        assertEquals(listOf<Pair<Long, Long?>>(0L to 1L), backend.fetched)

        // 向后跳转：与前缀之间有空隙，按整分段落盘（这一段的字节本来也要用）
        val middle = get("$base/audio/testkey", "bytes=100-199")
        assertEquals(206, middle.status)
        assertEquals("bytes 100-199/1000", middle.contentRange)
        assertContentEquals(payload.copyOfRange(100, 200), middle.body)
        assertEquals(
            listOf<Pair<Long, Long?>>(0L to 1L, 2L to 999L),
            backend.fetched,
            "跳转落在一个分段里：该分段写满才提交，因此取到段尾",
        )

        // 已缓存区间：不应再打上游
        val cached = get("$base/audio/testkey", "bytes=50-99")
        assertEquals(206, cached.status)
        assertContentEquals(payload.copyOfRange(50, 100), cached.body)
        assertEquals(2, backend.fetched.size, "命中缓存的请求不应访问上游")
    }

    @Test
    fun `serves the whole file when no range is requested`() = runTest {
        val base = startProxy()

        val response = get("$base/audio/testkey", range = null)

        assertEquals(200, response.status)
        assertEquals("bytes", response.acceptRanges)
        assertContentEquals(payload, response.body)
    }

    @Test
    fun `rejects a range that starts beyond the end of the file`() = runTest {
        val base = startProxy()

        val response = get("$base/audio/testkey", "bytes=5000-")

        assertEquals(416, response.status)
        assertEquals("bytes */1000", response.contentRange)
        assertTrue(backend.fetched.isEmpty(), "越界请求不应触发下载")
    }

    @Test
    fun `falls back to the whole file for multi range requests`() = runTest {
        val base = startProxy()

        val response = get("$base/audio/testkey", "bytes=0-1,5-6")

        assertEquals(200, response.status)
        assertContentEquals(payload, response.body)
    }

    @Test
    fun `returns 404 for a key that is no longer available`() = runTest {
        val base = startProxy()

        val response = get("$base/audio/unknown-key", "bytes=0-1")

        assertEquals(404, response.status)
    }

    @Test
    fun `grows the cache prefix so the whole track becomes available offline`() = runTest {
        backend = FakeBackend(payload)
        val cacheRoot = Files.createTempDirectory("lmusic-stream-proxy-full").toString()
        val cache = StreamCache(cacheRoot, "test", json)
        proxy = StreamProxy(cache, backend)
        val base = proxy.start()

        assertContentEquals(payload, get("$base/audio/testkey", range = null).body)

        assertEquals(1000L, cache.prefixSize("testkey"))
        assertNotNull(cache.readBytes("testkey", 0, 1000))
        assertContentEquals(payload, cache.readBytes("testkey", 0, 1000))

        // 全覆盖之后即使上游不可用，也应该能完整服务
        backend.failing = true
        val offline = get("$base/audio/testkey", "bytes=700-799")
        assertEquals(206, offline.status)
        assertContentEquals(payload.copyOfRange(700, 800), offline.body)
    }

    @Test
    fun `evicts the least recently used track once the cache exceeds the quota`() = runTest {
        val first = ByteArray(600) { index -> (index % 97).toByte() }
        val second = ByteArray(600) { index -> (index % 89).toByte() }
        backend = FakeBackend(mapOf("one" to first, "two" to second))
        val cacheRoot = Files.createTempDirectory("lmusic-stream-proxy-quota").toString()
        val cache = StreamCache(cacheRoot, "test", json)
        // 配额 900：放不下两首 600 字节的歌；节流设为 0 让淘汰在请求结束时立刻发生
        proxy = StreamProxy(
            cache = cache,
            backend = backend,
            quotaBytes = { 900L },
            evictionIntervalMillis = 0,
        )
        val base = proxy.start()

        assertContentEquals(first, get("$base/audio/one", range = null).body)
        assertEquals(600L, cache.prefixSize("one"))

        assertContentEquals(second, get("$base/audio/two", range = null).body)

        assertEquals(0L, cache.prefixSize("one"), "超出配额后最久未使用的应被淘汰")
        assertEquals(600L, cache.prefixSize("two"), "正在播放的这首不能被删")
    }

    @Test
    fun `rebuilds the cache when the remote file shrank`() = runTest {
        backend = FakeBackend(payload)
        val cacheRoot = Files.createTempDirectory("lmusic-stream-proxy-rebuild").toString()
        val cache = StreamCache(cacheRoot, "test", json)
        proxy = StreamProxy(cache, backend)
        val base = proxy.start()

        assertContentEquals(payload, get("$base/audio/testkey", range = null).body)
        assertEquals(1000L, cache.prefixSize("testkey"))

        // 远端换成更小的文件：旧前缀比新文件还长，必须整段重下而不是复用旧字节
        val shrunk = ByteArray(400) { 7 }
        backend = FakeBackend(shrunk)
        proxy.stop()

        val secondRoot = cacheRoot
        val secondCache = StreamCache(secondRoot, "test", json)
        proxy = StreamProxy(secondCache, backend)
        val secondBase = proxy.start()
        val response = get("$secondBase/audio/testkey", range = null)

        assertContentEquals(shrunk, response.body, "变小后必须返回新文件内容")
        assertEquals(400L, secondCache.prefixSize("testkey"))
    }

    // ── 区间覆盖与优先级 ──

    @Test
    fun `a forward seek does not download the part it skipped`() = runTest {
        val payload = ByteArray(4_000) { index -> (index % 251).toByte() }
        val base = startProxy(payload = payload, segmentSize = 1_000L)

        // 头部探测：正好接在前缀后，走前缀
        val head = get("$base/audio/testkey", "bytes=0-99")
        assertContentEquals(payload.copyOfRange(0, 100), head.body)
        assertEquals(100L, cache.prefixSize("testkey"))

        // 跳到 3000：只该取 3000 所在的那一段，不能把 100..2999 一起拖下来
        val jumped = get("$base/audio/testkey", "bytes=3000-3099")
        assertEquals(206, jumped.status)
        assertContentEquals(payload.copyOfRange(3000, 3100), jumped.body)
        assertEquals(
            listOf<Pair<Long, Long?>>(0L to 99L, 3000L to 3999L),
            backend.fetched,
            "被跳过的 100..2999 不该被取下来",
        )
        assertEquals(100L, cache.prefixSize("testkey"), "前缀不该被跳过的部分污染")
        assertEquals(
            listOf(CachedRange(0, 99), CachedRange(3000, 3999)),
            cache.coveredRanges("testkey", payload.size.toLong()),
        )

        // 回头听被跳过的那段：又接回前缀后面
        val rewind = get("$base/audio/testkey", "bytes=100-199")
        assertContentEquals(payload.copyOfRange(100, 200), rewind.body)
        assertEquals(200L, cache.prefixSize("testkey"))
    }

    @Test
    fun `keeps growing the prefix while playback continues sequentially`() = runTest {
        val payload = ByteArray(3_000) { index -> (index % 251).toByte() }
        val base = startProxy(payload = payload, segmentSize = 1_000L)

        get("$base/audio/testkey", "bytes=0-999")
        get("$base/audio/testkey", "bytes=1000-1999")

        assertEquals(2_000L, cache.prefixSize("testkey"), "顺序播放应一路接在前缀后面")
        assertEquals(emptyMap(), cache.segments("testkey"), "顺序播放不该产生分段")
    }

    @Test
    fun `fills the rest of the track from the playhead once playback starts`() = runTest {
        val payload = ByteArray(4_000) { index -> (index % 251).toByte() }
        val base = startProxy(
            payload = payload,
            segmentSize = 1_000L,
            lookaheadBytes = 0L,
            allowBackgroundFill = { true },
        )

        get("$base/audio/testkey", "bytes=0-499")

        awaitCondition { cache.coveredBytes("testkey", 4_000L) == 4_000L }
        assertEquals(
            listOf(CachedRange(0, 3_999)),
            cache.coveredRanges("testkey", 4_000L),
            "开播后应主动把整首补齐",
        )
    }

    @Test
    fun `background fill prefers the playhead over the skipped part`() = runTest {
        val payload = ByteArray(8_000) { index -> (index % 251).toByte() }
        var allowBackground = false
        val base = startProxy(
            payload = payload,
            segmentSize = 2_000L,
            lookaheadBytes = 0L,
            allowBackgroundFill = { allowBackground },
        )

        // 先用播放器请求铺出"播放头附近有、中间被跳过"的局面
        get("$base/audio/testkey", "bytes=0-999")
        get("$base/audio/testkey", "bytes=6000-6999")
        assertEquals(listOf<Pair<Long, Long?>>(0L to 999L, 6000L to 7999L), backend.fetched)

        // 播放头回到 2000：前方 2000..3999 还没缓存，被跳过的 1000..1999 也没缓存
        allowBackground = true
        proxy.notePlayhead("testkey", 2_000L)

        awaitCondition { backend.fetched.any { it.first == 2_000L } }
        assertEquals(
            2_000L to 3_999L,
            backend.fetched.drop(2).first(),
            "后台补齐应先补播放头那一段，而不是被跳过的 1000..1999",
        )
    }

    private suspend fun get(url: String, range: String?): Response = withContext(Dispatchers.IO) {
        val builder = HttpRequest.newBuilder(URI.create(url))
        if (range != null) builder.header("Range", range)
        val response = HttpClient.newHttpClient()
            .send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray())
        Response(
            status = response.statusCode(),
            body = response.body(),
            contentRange = response.headers().firstValue("Content-Range").orElse(null),
            acceptRanges = response.headers().firstValue("Accept-Ranges").orElse(null),
        )
    }

    private class Response(
        val status: Int,
        val body: ByteArray,
        val contentRange: String?,
        val acceptRanges: String? = null,
    )

    private class FakeBackend(
        private val payloads: Map<String, ByteArray>,
    ) : StreamBackend {
        constructor(payload: ByteArray, key: String = "testkey") : this(mapOf(key to payload))

        val fetched = mutableListOf<Pair<Long, Long?>>()
        var failing = false

        override suspend fun resolve(key: String): StreamTarget? =
            payloads[key]?.let { payload ->
                StreamTarget(
                    path = "/music/$key.flac",
                    totalSize = payload.size.toLong(),
                    contentType = "audio/flac",
                )
            }

        override suspend fun fetch(
            target: StreamTarget,
            start: Long,
            endInclusive: Long?,
            onChunk: suspend (ByteArray) -> Unit,
        ) {
            if (failing) throw IllegalStateException("上游不可用")
            fetched += start to endInclusive
            val payload = payloads.getValue(target.path.substringAfterLast('/').removeSuffix(".flac"))
            val last = (endInclusive ?: (payload.size - 1L)).coerceAtMost(payload.size - 1L)
            var position = start
            while (position <= last) {
                val size = minOf(4096L, last - position + 1L).toInt()
                onChunk(payload.copyOfRange(position.toInt(), position.toInt() + size))
                position += size
            }
        }
    }
}
