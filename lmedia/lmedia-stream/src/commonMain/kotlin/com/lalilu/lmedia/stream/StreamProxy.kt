package com.lalilu.lmedia.stream

import co.touchlab.kermit.Logger
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeBuffer
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 代理播放某个缓存键所需的信息。 */
data class StreamTarget(
    val path: String,
    val totalSize: Long,
    val contentType: String?,
)

/**
 * 代理与具体媒体来源之间的接口。
 *
 * 用接口而不是直接依赖某个数据源，既让代理与来源解耦（WebDAV / Subsonic / 以后任何能按区间
 * 取字节的来源都能复用），也便于脱离网络测试。
 */
interface StreamBackend {
    /** 解析缓存键；歌曲已不在当前快照中时返回 null。 */
    suspend fun resolve(key: String): StreamTarget?

    /**
     * 从远端拉取 `[start, endInclusive]`（闭区间；[endInclusive] 为 null 表示到文件末尾），
     * 按到达顺序回调。[onChunk] 返回后才会继续拉取下一段。
     */
    suspend fun fetch(
        target: StreamTarget,
        start: Long,
        endInclusive: Long?,
        onChunk: suspend (ByteArray) -> Unit,
    )
}

/**
 * 播放用的本机回环代理。
 *
 * 播放器只会看到 `http://127.0.0.1:<port>/audio/<key>`，凭据留在这一侧；字节边转发边写进本地
 * 缓存，于是"听过的部分"自动变成离线缓存与后续元数据提取的输入。
 *
 * 缓存是只追加的前缀：请求落在已缓存区间内直接读本地；落在后面则先补齐中间那段（写进缓存但不
 * 发给播放器，因为播放器没要），再把请求区间边下边发。
 */
@OptIn(ExperimentalTime::class)
class StreamProxy(
    private val cache: StreamCache,
    private val backend: StreamBackend,
    /** 每次缓存增长后回调，供上层判断 头部窗口 / 100% 提取节点。 */
    private val onCacheProgress: suspend (key: String) -> Unit = {},
    /** 音频缓存容量上限；<= 0 表示不限制。每次需要时读取，改配置立即生效。 */
    private val quotaBytes: () -> Long = { 0L },
    /** 淘汰检查的最小间隔：跳过它只为避免每次请求都扫一遍缓存目录。 */
    private val evictionIntervalMillis: Long = EVICTION_INTERVAL_MILLIS,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    companion object {
        private const val TAG = "StreamProxy"
        internal const val LOOPBACK = "127.0.0.1"
        internal const val ROUTE_PREFIX = "/audio/"

        /** 淘汰检查间隔：容量是稳态约束而不是硬保证，5 秒粒度足够。 */
        internal const val EVICTION_INTERVAL_MILLIS = 5_000L
    }

    private val logger = Logger.withTag(TAG)
    private var server: EmbeddedServer<*, *>? = null
    private var baseUrl: String? = null
    private val keyLocks = mutableMapOf<String, Mutex>()
    private val keyLocksGuard = Mutex()
    private var lastEvictionAt = 0L

    /** 正在被读取或写入的键：淘汰时绝不动它们。 */
    private val activeKeys = mutableSetOf<String>()

    /** 启动代理并返回基地址；重复调用返回同一地址。 */
    suspend fun start(): String {
        baseUrl?.let { return it }
        cache.ensureReady()

        val started = embeddedServer(CIO, port = 0, host = LOOPBACK) { proxyModule() }
        started.startSuspend(wait = false)
        val port = started.engine.resolvedConnectors().first().port
        server = started
        return "http://$LOOPBACK:$port".also { baseUrl = it }
    }

    suspend fun stop() {
        server?.stopSuspend()
        server = null
        baseUrl = null
        cache.persistUsage()
    }

    /** 播放地址；代理未启动时返回 null（调用方必须据此拒绝播放，不能回退到直链）。 */
    fun audioUrl(key: String): String? = baseUrl?.let { "$it$ROUTE_PREFIX$key" }

    /**
     * 主动把缓存补到 [upTo]（含）——用于"允许后台主动下载"时补全尚未播放过的歌曲。
     *
     * 与播放请求共用同一把 key 锁，因此不会与正在进行的播放交错；不向任何客户端发送数据。
     * 返回补齐后的已缓存长度。
     */
    suspend fun ensureCached(
        key: String,
        target: StreamTarget,
        upTo: Long,
    ): Long = lockFor(key).withLock {
        activeKeys += key
        try {
            val last = if (target.totalSize > 0L) {
                minOf(upTo, target.totalSize - 1)
            } else {
                upTo
            }.takeIf { it >= 0L } ?: return@withLock cache.filledSize(key)

            // 远端文件被换成更小的文件时，旧缓存的前缀比新文件还长，必须重建
            cache.ensureConsistent(key, target.totalSize)
            val cached = cache.filledSize(key)
            if (cached > last) {
                cache.touch(key, target.totalSize)
                return@withLock cached
            }

            val sink = cache.openAppendSink(key)
            try {
                backend.fetch(
                    target = target,
                    start = cached,
                    endInclusive = if (target.totalSize > 0L) last else null,
                ) { bytes -> sink.write(bytes) }
                sink.flush()
            } finally {
                sink.close()
            }
            cache.touch(key, target.totalSize)
            onCacheProgress(key)
            evictIfNeeded()
            cache.filledSize(key)
        } finally {
            activeKeys -= key
        }
    }

    /** 按配额淘汰；调用点做节流，避免每次请求都扫一遍缓存目录。 */
    private fun evictIfNeeded() {
        val quota = quotaBytes()
        if (quota <= 0L) return
        val now = clock()
        if (now - lastEvictionAt < evictionIntervalMillis) return
        lastEvictionAt = now
        val evicted = cache.evictIfNeeded(quotaBytes = quota, pinned = activeKeys)
        if (evicted.isNotEmpty()) {
            logger.i(messageString = "缓存超出上限，淘汰 ${evicted.size} 首：${evicted.take(5)}")
        }
    }

    private fun Application.proxyModule() {
        routing {
            get("/audio/{key}") { call.serveAudio(head = false) }
            head("/audio/{key}") { call.serveAudio(head = true) }
        }
    }

    private suspend fun ApplicationCall.serveAudio(head: Boolean) {
        val key = parameters["key"].orEmpty()
        val target = backend.resolve(key)
        if (target == null) {
            respond(HttpStatusCode.NotFound, "Unknown media key")
            return
        }

        val total = target.totalSize
        val resolution = HttpRange.resolve(request.headers[HttpHeaders.Range], total)

        if (resolution.disposition == RangeDisposition.UNSATISFIABLE) {
            response.header(HttpHeaders.ContentRange, "bytes */$total")
            respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        }

        val contentType = target.contentType
            ?.let { raw -> runCatching { ContentType.parse(raw) }.getOrNull() }
            ?: ContentType.Application.OctetStream
        response.header(HttpHeaders.AcceptRanges, "bytes")

        // 服务器没给长度：无法承诺 Range，只能整文件透传（仍会写进缓存）
        if (total <= 0L) {
            serveUnknownLength(key, target, contentType, head)
            return
        }

        val range = resolution.range ?: ByteRange(0L, total - 1)
        val isPartial = resolution.disposition == RangeDisposition.SATISFIABLE

        // 远端文件被替换成更小的文件时，旧缓存前缀比新文件还长，必须重建
        cache.ensureConsistent(key, total)
        cache.touch(key, total)

        if (isPartial) {
            response.header(
                HttpHeaders.ContentRange,
                "bytes ${range.start}-${range.endInclusive}/$total",
            )
        }

        if (head) {
            // HEAD 只需要元信息；不触发下载
            response.header(HttpHeaders.ContentLength, range.length)
            respond(if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK)
            return
        }

        respondBytesWriter(
            contentType = contentType,
            status = if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK,
            contentLength = range.length,
        ) {
            serveRange(key, target, range, this)
        }
    }

    private suspend fun ApplicationCall.serveUnknownLength(
        key: String,
        target: StreamTarget,
        contentType: ContentType,
        head: Boolean,
    ) {
        if (head) {
            respond(HttpStatusCode.OK)
            return
        }
        respondBytesWriter(contentType = contentType, status = HttpStatusCode.OK) {
            activeKeys += key
            try {
                cache.touch(key, target.totalSize)
                val cached = cache.filledSize(key)
                if (cached > 0L) {
                    cache.openSource(key, 0L)?.use { source -> writeBuffer(source, cached) }
                }
                val sink = cache.openAppendSink(key)
                try {
                    backend.fetch(target, cached, endInclusive = null) { bytes ->
                        sink.write(bytes)
                        writeFully(bytes)
                    }
                    sink.flush()
                } finally {
                    sink.close()
                }
                cache.touch(key, target.totalSize)
                onCacheProgress(key)
                evictIfNeeded()
            } finally {
                activeKeys -= key
            }
        }
    }

    private suspend fun serveRange(
        key: String,
        target: StreamTarget,
        range: ByteRange,
        channel: ByteWriteChannel,
    ) {
        lockFor(key).withLock {
            activeKeys += key
            try {
                serveRangeLocked(key, target, range, channel)
            } finally {
                activeKeys -= key
            }
        }
    }

    private suspend fun serveRangeLocked(
        key: String,
        target: StreamTarget,
        range: ByteRange,
        channel: ByteWriteChannel,
    ) {
        var position = range.start
        val cached = cache.filledSize(key)

        // 已缓存的前缀部分：直接读本地，不产生网络请求
        if (position < cached) {
            val available = minOf(range.endInclusive, cached - 1) - position + 1
            cache.openSource(key, position)?.use { source -> channel.writeBuffer(source, available) }
            position += available
        }

        if (position > range.endInclusive) {
            onCacheProgress(key)
            return
        }

        // 剩余部分：远端从"已缓存前缀"继续拉，保持缓存仍是连续前缀。
        // 落在 [cached, position) 的字节写进缓存但不发给播放器——播放器没要它们。
        val sink = cache.openAppendSink(key)
        var served = 0L
        try {
            var toDrop = position - cached
            backend.fetch(target, cached, range.endInclusive) { bytes ->
                // 先落盘：即使随后客户端断开，已收到的字节依然是有效的缓存前缀
                sink.write(bytes)
                var from = 0
                if (toDrop > 0L) {
                    val drop = minOf(toDrop, bytes.size.toLong()).toInt()
                    toDrop -= drop
                    from = drop
                }
                if (from < bytes.size) {
                    channel.writeFully(bytes, from, bytes.size)
                    served += bytes.size - from
                }
            }
            sink.flush()
        } finally {
            sink.close()
        }

        val expected = range.endInclusive - position + 1
        if (served < expected) {
            logger.w(messageString = "上游提前结束：key=$key 期望 $expected 字节，实际 $served")
        }
        cache.touch(key, target.totalSize)
        onCacheProgress(key)
        evictIfNeeded()
    }

    private suspend fun lockFor(key: String): Mutex = keyLocksGuard.withLock {
        keyLocks.getOrPut(key) { Mutex() }
    }
}
