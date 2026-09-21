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

    @AfterTest
    fun tearDown() = runTest {
        if (::proxy.isInitialized) proxy.stop()
    }

    private suspend fun startProxy(): String {
        backend = FakeBackend(payload)
        val cacheRoot = Files.createTempDirectory("lmusic-stream-proxy").toString()
        proxy = StreamProxy(StreamCache(cacheRoot, "test", json), backend)
        return proxy.start()
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

        // 向后跳转：中间那段要一起补下来（写进缓存但不发给播放器）
        val middle = get("$base/audio/testkey", "bytes=100-199")
        assertEquals(206, middle.status)
        assertEquals("bytes 100-199/1000", middle.contentRange)
        assertContentEquals(payload.copyOfRange(100, 200), middle.body)
        assertEquals(
            listOf<Pair<Long, Long?>>(0L to 1L, 2L to 199L),
            backend.fetched,
            "向跳转时应从已缓存前缀处补齐中间那段",
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
