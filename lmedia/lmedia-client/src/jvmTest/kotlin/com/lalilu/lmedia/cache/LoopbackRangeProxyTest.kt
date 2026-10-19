package com.lalilu.lmedia.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
class LoopbackRangeProxyTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val payload = ByteArray(1000) { index -> (index % 251).toByte() }
    private lateinit var proxy: LoopbackRangeProxy
    private lateinit var upstreamFake: FakeBackend

    @AfterTest
    fun tearDown() = runTest {
        if (::proxy.isInitialized) proxy.stop()
    }

    private suspend fun startProxy(): String {
        upstreamFake = FakeBackend(payload)
        val cacheRoot = Files.createTempDirectory("lmusic-webdav-proxy").toString()
        proxy = LoopbackRangeProxy(MediaCacheStore(cacheRoot, json, namespace = "test"), upstreamFake)
        return proxy.start()
    }

    @Test
    fun `serves the probe range then reuses the cache for later requests`() = runTest {
        upstreamFake = FakeBackend(payload)
        val cacheRoot = Files.createTempDirectory("lmusic-webdav-proxy-probe").toString()
        val cache = MediaCacheStore(cacheRoot, json, namespace = "test")
        proxy = LoopbackRangeProxy(cache, upstreamFake)
        val base = proxy.start()

        // AVPlayer 会先发 2 字节探测：只回这 2 字节，但顺带把整首补进缓存
        val probe = get("$base/audio/testkey", "bytes=0-1")
        assertEquals(206, probe.status)
        assertEquals("bytes 0-1/1000", probe.contentRange)
        assertContentEquals(payload.copyOfRange(0, 2), probe.body)

        // 探测只回 2 字节，下载不受它限制：整首仍会被补齐
        awaitFilled(cache, "testkey", payload.size.toLong())
        assertEquals(
            listOf<Pair<Long, Long?>>(0L to null),
            upstreamFake.fetched,
            "一次请求就够了：整首由同一个下载任务补齐",
        )

        // 向后跳转：缓存里已经有中间那段，不应再打上游
        val middle = get("$base/audio/testkey", "bytes=100-199")
        assertEquals(206, middle.status)
        assertEquals("bytes 100-199/1000", middle.contentRange)
        assertContentEquals(payload.copyOfRange(100, 200), middle.body)

        val cached = get("$base/audio/testkey", "bytes=50-99")
        assertEquals(206, cached.status)
        assertContentEquals(payload.copyOfRange(50, 100), cached.body)
        assertEquals(1, upstreamFake.fetched.size, "命中缓存的请求不应访问上游")
    }

    /** 等缓存补齐到指定长度：下载在代理自己的作用域里跑，测试需要给它一点时间。 */
    private suspend fun awaitFilled(cache: MediaCacheStore, key: String, size: Long) {
        repeat(100) {
            if (cache.filledSize(key) >= size) return
            kotlinx.coroutines.delay(20)
        }
        assertEquals(size, cache.filledSize(key), "缓存未在预期时间内补齐")
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
        assertTrue(upstreamFake.fetched.isEmpty(), "越界请求不应触发下载")
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
        upstreamFake = FakeBackend(payload)
        val cacheRoot = Files.createTempDirectory("lmusic-webdav-proxy-full").toString()
        val cache = MediaCacheStore(cacheRoot, json, namespace = "test")
        proxy = LoopbackRangeProxy(cache, upstreamFake)
        val base = proxy.start()

        assertContentEquals(payload, get("$base/audio/testkey", range = null).body)

        assertEquals(1000L, cache.filledSize("testkey"))
        assertNotNull(cache.readBytes("testkey", 0, 1000))
        assertContentEquals(payload, cache.readBytes("testkey", 0, 1000))

        // 全覆盖之后即使上游不可用，也应该能完整服务
        upstreamFake.failing = true
        val offline = get("$base/audio/testkey", "bytes=700-799")
        assertEquals(206, offline.status)
        assertContentEquals(payload.copyOfRange(700, 800), offline.body)
    }

    @Test
    fun `playback reads bytes while a background prefetch is still downloading`() = runTest {
        upstreamFake = FakeBackend(payload)
        val cacheRoot = Files.createTempDirectory("lmusic-webdav-proxy-share").toString()
        val cache = MediaCacheStore(cacheRoot, json, namespace = "test")
        proxy = LoopbackRangeProxy(cache, upstreamFake)
        val base = proxy.start()

        // 后台整首预取：上游只吐一小段就卡住，模拟慢速 NAS
        val gate = CompletableDeferred<Unit>()
        upstreamFake.blockAfterBytes = 100
        upstreamFake.gate = gate
        val prefetch = async {
            proxy.ensureCached(
                key = "testkey",
                target = UpstreamTarget("testkey", payload.size.toLong(), "audio/flac"),
                upTo = Long.MAX_VALUE,
            )
        }
        upstreamFake.awaitEntered()

        // 播放请求必须能立刻拿到已经落盘的那一段，而不是等整首下完（以前会卡在 key 锁上读超时）
        val response = get("$base/audio/testkey", "bytes=0-99")
        assertEquals(206, response.status, "播放不应被后台下载挡住")
        assertContentEquals(payload.copyOfRange(0, 100), response.body)

        // 放行上游：剩下的字节继续补完，缓存最终是完整前缀
        gate.complete(Unit)
        prefetch.await()
        assertEquals(payload.size.toLong(), cache.filledSize("testkey"), "下载应继续把整首补齐")
    }

    @Test
    fun `evicts the least recently used track once the cache exceeds the quota`() = runTest {
        val first = ByteArray(600) { index -> (index % 97).toByte() }
        val second = ByteArray(600) { index -> (index % 89).toByte() }
        upstreamFake = FakeBackend(mapOf("one" to first, "two" to second))
        val cacheRoot = Files.createTempDirectory("lmusic-webdav-proxy-quota").toString()
        val cache = MediaCacheStore(cacheRoot, json, namespace = "test")
        // 配额 900：放不下两首 600 字节的歌；节流设为 0 让淘汰在请求结束时立刻发生
        proxy = LoopbackRangeProxy(
            cache = cache,
            upstream = upstreamFake,
            quotaBytes = { 900L },
            evictionIntervalMillis = 0,
        )
        val base = proxy.start()

        assertContentEquals(first, get("$base/audio/one", range = null).body)
        assertEquals(600L, cache.filledSize("one"))

        assertContentEquals(second, get("$base/audio/two", range = null).body)

        assertEquals(0L, cache.filledSize("one"), "超出配额后最久未使用的应被淘汰")
        assertEquals(600L, cache.filledSize("two"), "正在播放的这首不能被删")
    }

    @Test
    fun `rebuilds the cache when the remote file shrank`() = runTest {
        upstreamFake = FakeBackend(payload)
        val cacheRoot = Files.createTempDirectory("lmusic-webdav-proxy-rebuild").toString()
        val cache = MediaCacheStore(cacheRoot, json, namespace = "test")
        proxy = LoopbackRangeProxy(cache, upstreamFake)
        val base = proxy.start()

        assertContentEquals(payload, get("$base/audio/testkey", range = null).body)
        assertEquals(1000L, cache.filledSize("testkey"))

        // 远端换成更小的文件：旧前缀比新文件还长，必须整段重下而不是复用旧字节
        val shrunk = ByteArray(400) { 7 }
        upstreamFake = FakeBackend(shrunk)
        proxy.stop()

        val secondRoot = cacheRoot
        val secondCache = MediaCacheStore(secondRoot, json, namespace = "test")
        proxy = LoopbackRangeProxy(secondCache, upstreamFake)
        val secondBase = proxy.start()
        val response = get("$secondBase/audio/testkey", range = null)

        assertContentEquals(shrunk, response.body, "变小后必须返回新文件内容")
        assertEquals(400L, secondCache.filledSize("testkey"))
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
    ) : RangeUpstream {
        constructor(payload: ByteArray, key: String = "testkey") : this(mapOf(key to payload))

        val fetched = mutableListOf<Pair<Long, Long?>>()
        var failing = false

        /** 吐够这么多字节后卡住，用来模拟"后台正在下载同一首"的时序。 */
        var blockAfterBytes = Long.MAX_VALUE
        var gate: CompletableDeferred<Unit>? = null
        private val entered = CompletableDeferred<Unit>()

        suspend fun awaitEntered() = entered.await()

        override suspend fun resolve(key: String): UpstreamTarget? =
            payloads[key]?.let { payload ->
                UpstreamTarget(
                    token = "/music/$key.flac",
                    totalSize = payload.size.toLong(),
                    contentType = "audio/flac",
                )
            }

        override suspend fun fetch(
            target: UpstreamTarget,
            start: Long,
            endInclusive: Long?,
            onChunk: suspend (ByteArray) -> Unit,
        ) {
            entered.complete(Unit)
            if (failing) throw IllegalStateException("上游不可用")
            fetched += start to endInclusive
            val payload = payloads.getValue(target.token.substringAfterLast('/').removeSuffix(".flac"))
            val last = (endInclusive ?: (payload.size - 1L)).coerceAtMost(payload.size - 1L)
            var position = start
            while (position <= last) {
                if (position >= start + blockAfterBytes) gate?.await()
                val size = minOf(4096L, last - position + 1L).toInt()
                onChunk(payload.copyOfRange(position.toInt(), position.toInt() + size))
                position += size
            }
        }
    }
}
