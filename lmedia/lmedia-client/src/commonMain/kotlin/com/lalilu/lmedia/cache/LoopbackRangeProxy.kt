package com.lalilu.lmedia.cache

import com.lalilu.lmedia.cache.MediaCacheStore
import com.lalilu.lmedia.cache.RangeDisposition
import com.lalilu.lmedia.cache.RangeHeader
import com.lalilu.lmedia.cache.RequestedRange
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 代理播放某个缓存键所需的信息。 */
internal data class UpstreamTarget(
    /** 上游自己解释的不透明标识：WebDAV 是远端路径，别的源可以是 id。 */
    val token: String,
    val totalSize: Long,
    val contentType: String?,
)

/** 代理与数据源之间的回调接口；用接口而不是直接依赖 WebDavSource，便于脱离网络测试。 */
internal interface RangeUpstream {
    /** 解析缓存键；歌曲已不在当前快照中时返回 null。 */
    suspend fun resolve(key: String): UpstreamTarget?

    /**
     * 从远端拉取 `[start, endInclusive]`（闭区间；[endInclusive] 为 null 表示到文件末尾），
     * 按到达顺序回调。[onChunk] 返回后才会继续拉取下一段。
     */
    suspend fun fetch(
        target: UpstreamTarget,
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
internal class LoopbackRangeProxy(
    private val cache: MediaCacheStore,
    private val upstream: RangeUpstream,
    /** 每次缓存增长后回调，供上层判断 30% / 100% 提取节点。 */
    private val onCacheProgress: suspend (key: String) -> Unit = {},
    /** 音频缓存容量上限；<= 0 表示不限制。每次需要时读取，改配置立即生效。 */
    private val quotaBytes: () -> Long = { 0L },
    /** 淘汰检查的最小间隔：跳过它只为避免每次请求都扫一遍缓存目录。 */
    private val evictionIntervalMillis: Long = EVICTION_INTERVAL_MILLIS,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    companion object {
        private const val TAG = "LoopbackRangeProxy"
        internal const val LOOPBACK = "127.0.0.1"
        internal const val ROUTE_PREFIX = "/audio/"

        /** 淘汰检查间隔：容量是稳态约束而不是硬保证，5 秒粒度足够。 */
        internal const val EVICTION_INTERVAL_MILLIS = 5_000L

        /** 等下载推进的轮询间隔：播放器读超时是 8 秒，这个粒度远小于它。 */
        private const val READ_WAIT_POLL_MILLIS = 50L
    }

    private val logger = Logger.withTag(TAG)
    private var server: EmbeddedServer<*, *>? = null
    private var baseUrl: String? = null
    private val keyLocks = mutableMapOf<String, Mutex>()
    private val keyLocksGuard = Mutex()

    /**
     * 每个键最多只有一个"填缓存"的任务（单飞）。
     *
     * 播放读取与后台预取共用它：读的人只等自己需要的那几个字节落盘，不跟整首下载抢锁——
     * 以前播放要等整首下完才拿到锁，慢速源上这就是播放器 8 秒读超时的直接原因。
     * 任务跑在自己的作用域里，不与调用方的 Job 绑定：调用方被取消不该中断已经开始的下载。
     */
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val downloads = mutableMapOf<String, Job>()
    private val downloadsGuard = Mutex()
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
     * 与播放读取共用同一个下载任务，因此不会重复下载，也不会让播放等它。
     */
    internal suspend fun ensureCached(
        key: String,
        target: UpstreamTarget,
        upTo: Long,
    ): Long {
        val job = ensureDownloading(key, target)
        val goal = if (target.totalSize > 0L) minOf(upTo, target.totalSize - 1) else upTo
        while (true) {
            val filled = cache.filledSize(key)
            if (filled > goal) return filled
            if (!job.isActive) return filled
            delay(READ_WAIT_POLL_MILLIS)
        }
    }

    /** 保证这个键有一个正在推进的下载任务并返回它；已有任务直接复用。 */
    private suspend fun ensureDownloading(key: String, target: UpstreamTarget): Job =
        downloadsGuard.withLock {
            downloads[key]?.takeIf { it.isActive }?.let { return@withLock it }
            val job = downloadScope.launch {
                // 写入串行化：缓存是只追加的连续前缀，两个写者会把它写坏
                lockFor(key).withLock { downloadLocked(key, target) }
            }
            downloads[key] = job
            job
        }

    /**
     * 把缓存补到文件末尾（长度未知时补到上游结束）。
     *
     * 一次补齐而不是只补到某个偏移：读的人随时可以取走已经落盘的部分，多下的那截本来也会
     * 成为离线缓存，不存在"为了一次 seek 白下"的问题。
     */
    private suspend fun downloadLocked(key: String, target: UpstreamTarget) {
        activeKeys += key
        try {
            // 远端文件被换成更小的文件时，旧缓存的前缀比新文件还长，必须重建
            cache.ensureConsistent(key, target.totalSize)
            val cached = cache.filledSize(key)
            if (target.totalSize > 0L && cached >= target.totalSize) {
                cache.touch(key, target.totalSize)
                return
            }

            logger.i {
                "开始补缓存 key=$key 从 ${cached}B 到 " +
                    if (target.totalSize > 0L) "${target.totalSize}B" else "上游结束"
            }
            val startedAt = clock()
            val sink = cache.openAppendSink(key)
            try {
                upstream.fetch(target = target, start = cached, endInclusive = null) { bytes ->
                    sink.write(bytes)
                }
                sink.flush()
            } finally {
                sink.close()
            }
            cache.touch(key, target.totalSize)
            logger.i {
                "补缓存完成 key=$key 共 ${cache.filledSize(key)}B 用时 ${clock() - startedAt}ms"
            }
            onCacheProgress(key)
            evictIfNeeded()
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
        val target = upstream.resolve(key)
        if (target == null) {
            respond(HttpStatusCode.NotFound, "Unknown media key")
            return
        }

        val total = target.totalSize
        val resolution = RangeHeader.resolve(request.headers[HttpHeaders.Range], total)

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

        val range = resolution.range ?: RequestedRange(0L, total - 1)
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
        target: UpstreamTarget,
        contentType: ContentType,
        head: Boolean,
    ) {
        if (head) {
            respond(HttpStatusCode.OK)
            return
        }
        val download = ensureDownloading(key, target)
        respondBytesWriter(contentType = contentType, status = HttpStatusCode.OK) {
            streamFromCache(
                key = key,
                range = 0L..Long.MAX_VALUE,
                download = download,
                channel = this,
            )
        }
    }

    private suspend fun serveRange(
        key: String,
        target: UpstreamTarget,
        range: RequestedRange,
        channel: ByteWriteChannel,
    ) {
        activeKeys += key
        try {
            // 缓存已经覆盖这次请求：纯读本地，不碰网络
            if (range.endInclusive < cache.filledSize(key)) {
                streamFromCache(key, range.start..range.endInclusive, download = null, channel = channel)
                onCacheProgress(key)
                return
            }

            val download = ensureDownloading(key, target)
            val served = streamFromCache(
                key = key,
                range = range.start..range.endInclusive,
                download = download,
                channel = channel,
            )
            if (served < range.length) {
                logger.w {
                    "上游提前结束：key=$key 期望 ${range.length} 字节，实际 $served" +
                        "（下载任务${if (download.isActive) "仍在进行" else "已结束"}）"
                }
            }
            onCacheProgress(key)
            evictIfNeeded()
        } finally {
            activeKeys -= key
        }
    }

    /**
     * 把 `[range]` 里的字节发给客户端：已落盘的部分直接读，没落盘的部分等下载任务推进。
     *
     * 读的是"已完整写入的连续前缀"（[MediaCacheStore.filledSize]），因此不需要和写者抢锁；
     * 下载任务结束（或失败）时不再干等，剩下的按截断处理，由客户端决定重试。
     */
    private suspend fun streamFromCache(
        key: String,
        range: LongRange,
        download: Job?,
        channel: ByteWriteChannel,
    ): Long {
        var position = range.first
        var served = 0L
        var lastFilled = -1L

        while (position <= range.last) {
            val filled = cache.filledSize(key)
            if (position < filled) {
                val available = minOf(range.last, filled - 1) - position + 1
                cache.openSource(key, position)?.use { source -> channel.writeBuffer(source, available) }
                position += available
                served += available
                continue
            }
            if (download == null || !download.isActive) break
            if (filled == lastFilled) delay(READ_WAIT_POLL_MILLIS) else lastFilled = filled
        }

        if (served > 0L) {
            logger.i {
                "代理完成 key=$key range=${range.first}-${range.last} 已发 ${served}B " +
                    "缓存=${cache.filledSize(key)}"
            }
        }
        return served
    }

    private suspend fun lockFor(key: String): Mutex = keyLocksGuard.withLock {
        keyLocks.getOrPut(key) { Mutex() }
    }
}
