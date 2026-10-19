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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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

        /** 等上游首字节的上限：超过它就先回一个明确的失败，而不是让播放器干等到自己超时。 */
        private const val FIRST_BYTE_TIMEOUT_MILLIS = 4_000L

        /** 下载过程中的进度上报节拍：提取节点靠它触发，1MB 粒度既够快又不会刷屏。 */
        private const val PROGRESS_STEP_BYTES = 1L * 1024 * 1024
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
    private val downloadScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            // 兜底：下载任务里的任何漏网异常都不允许掀掉整个进程
            logger.w(messageString = "补缓存任务异常：${throwable.message}", throwable = throwable)
        }
    )
    private val downloads = mutableMapOf<String, Download>()
    private val downloadsGuard = Mutex()
    private var lastEvictionAt = 0L

    /**
     * 正在被读取或写入的键：淘汰时绝不动它们。
     *
     * 这些字段会被多个协程并发访问（每个播放请求一个、下载任务一个、淘汰逻辑一个），
     * 因此全部由 [activeKeysGuard] 保护——直接把活的可变集合交给淘汰逻辑迭代，
     * 会在并发时抛 `ConcurrentModificationException`，或者把正在读的文件删掉。
     */
    private val activeKeys = mutableSetOf<String>()
    private val activeKeysGuard = Mutex()

    /** 正在播放的键：即使暂停中、没有在途请求，也不允许被淘汰。 */
    private var playbackKey: String? = null

    /** 每个键的下载任务及其读者计数/失败原因。 */
    private class Download(
        val job: Job,
        /** 由后台预取启动：没有读者附着时可以被"暂停后台传输"取消。 */
        val startedByPrefetch: Boolean,
    ) {
        var readers: Int = 0
        var error: Throwable? = null
    }

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
        val entry = ensureDownloading(key, target, prefetch = true)
        val goal = if (target.totalSize > 0L) minOf(upTo, target.totalSize - 1) else upTo
        try {
            while (true) {
                val filled = cache.filledSize(key)
                if (filled > goal) return filled
                if (!entry.job.isActive) return filled
                delay(READ_WAIT_POLL_MILLIS)
            }
        } catch (cancelled: CancellationException) {
            // 后台预取被取消（关开关 / 网络门控）：没有播放请求在用它就立刻停掉下载，
            // 不能因为"等待方走了"还继续占着带宽。
            cancelIfIdle(entry)
            throw cancelled
        }
    }

    /**
     * 停掉所有"没有播放请求在用"的后台预取。
     *
     * 播放读取附着的下载不受影响——用户此刻在听，那份流量是他要的。
     */
    internal suspend fun cancelBackgroundDownloads() {
        val idle = downloadsGuard.withLock {
            downloads.values.filter { it.startedByPrefetch && it.readers == 0 }
        }
        if (idle.isEmpty()) return
        logger.i { "暂停后台传输：取消 ${idle.size} 个空闲预取" }
        idle.forEach { it.job.cancel() }
    }

    /** 记录当前播放的键：它不能被淘汰，哪怕还没有在途请求。 */
    suspend fun pinPlayback(key: String?) {
        activeKeysGuard.withLock { playbackKey = key }
    }

    /** 保证这个键有一个正在推进的下载任务并返回它；已有任务直接复用。 */
    private suspend fun ensureDownloading(
        key: String,
        target: UpstreamTarget,
        prefetch: Boolean = false,
    ): Download = downloadsGuard.withLock {
        downloads[key]?.takeIf { it.job.isActive }?.let { return@withLock it }

        // 任务体要读到自己这条记录（记录失败原因 / 结束时把自己从表里摘掉），
        // 因此先声明再赋值；LAZY 启动保证赋值发生在任务真正开跑之前。
        lateinit var entry: Download
        val job = downloadScope.launch(start = CoroutineStart.LAZY) {
            try {
                // 写入串行化：缓存是只追加的连续前缀，两个写者会把它写坏
                lockFor(key).withLock { downloadLocked(key, target) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                // 上游 404/断网都是正常业务结果：记下原因让读端回明确的状态码。
                // 这里绝不能让异常逃出任务——它是独立作用域里 launch 出来的，逃出去就是进程崩溃。
                entry.error = throwable
                logger.w(messageString = "补缓存失败 key=$key：${throwable.message}")
            } finally {
                // 完成的记录必须摘掉，否则每听一首就多留一条（任务对象本身也不小）
                downloadsGuard.withLock {
                    if (downloads[key] === entry) downloads.remove(key)
                }
            }
        }
        entry = Download(job = job, startedByPrefetch = prefetch)
        downloads[key] = entry
        job.start()
        entry
    }

    /** 播放请求附着到下载任务上：它在这段时间里不能被"暂停后台传输"取消。 */
    private suspend fun acquireForRead(key: String, target: UpstreamTarget): Download {
        val entry = ensureDownloading(key, target)
        downloadsGuard.withLock { entry.readers += 1 }
        return entry
    }

    private suspend fun releaseForRead(entry: Download) {
        downloadsGuard.withLock { entry.readers = (entry.readers - 1).coerceAtLeast(0) }
    }

    private suspend fun cancelIfIdle(entry: Download) {
        val idle = downloadsGuard.withLock { entry.readers == 0 }
        if (idle) entry.job.cancel()
    }

    /**
     * 把缓存补到文件末尾（长度未知时补到上游结束）。
     *
     * 一次补齐而不是只补到某个偏移：读的人随时可以取走已经落盘的部分，多下的那截本来也会
     * 成为离线缓存，不存在"为了一次 seek 白下"的问题。
     */
    private suspend fun downloadLocked(key: String, target: UpstreamTarget) {
        markActive(key)
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
            var written = 0L
            var nextReport = PROGRESS_STEP_BYTES
            try {
                upstream.fetch(target = target, start = cached, endInclusive = null) { bytes ->
                    sink.write(bytes)
                    written += bytes.size
                    // 边下边报进度：提取要能在 1MB / 30% 这些早期节点就介入，
                    // 不能等整个请求结束（整首一次请求就再没有"中间时刻"了）
                    if (cached + written >= nextReport) {
                        nextReport = (cached + written) / PROGRESS_STEP_BYTES * PROGRESS_STEP_BYTES +
                            PROGRESS_STEP_BYTES
                        onCacheProgress(key)
                    }
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
            // LRU 账目只在淘汰和 stop 时落盘的话，生产环境（代理常驻）几乎不会写；
            // 每首补完顺手落一次，进程被杀也不会退回"按文件 mtime 淘汰"。
            cache.persistUsage()
            evictIfNeeded()
        } finally {
            unmarkActive(key)
        }
    }

    /** 按配额淘汰；调用点做节流，避免每次请求都扫一遍缓存目录。 */
    private suspend fun evictIfNeeded() {
        val quota = quotaBytes()
        if (quota <= 0L) return
        val now = clock()
        if (now - lastEvictionAt < evictionIntervalMillis) return
        lastEvictionAt = now
        // 取快照而不是把活集合交出去：淘汰过程会遍历它，而读者随时可能在增删。
        val pinned = activeKeysGuard.withLock {
            buildSet {
                addAll(activeKeys)
                playbackKey?.let(::add)
            }
        }
        val evicted = cache.evictIfNeeded(quotaBytes = quota, pinned = pinned)
        if (evicted.isNotEmpty()) {
            logger.i(messageString = "缓存超出上限，淘汰 ${evicted.size} 首：${evicted.take(5)}")
        }
    }

    private suspend fun markActive(key: String) {
        activeKeysGuard.withLock { activeKeys += key }
    }

    private suspend fun unmarkActive(key: String) {
        activeKeysGuard.withLock { activeKeys -= key }
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

        // 先确认"这段能拿到"或"上游确实失败了"，再决定状态码：
        // 先回 206 再截断，播放器只能看到一个模糊的 Source error。
        val entry = acquireForRead(key, target)
        try {
            if (!awaitRangeReady(key, range.start, entry)) {
                respond(upstreamStatusOf(entry.error))
                return
            }
            respondBytesWriter(
                contentType = contentType,
                status = if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK,
                contentLength = range.length,
            ) {
                serveRange(key, range, entry, this)
            }
        } finally {
            releaseForRead(entry)
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
        val entry = acquireForRead(key, target)
        try {
            if (!awaitRangeReady(key, start = 0L, entry = entry)) {
                respond(upstreamStatusOf(entry.error))
                return
            }
            respondBytesWriter(contentType = contentType, status = HttpStatusCode.OK) {
                streamFromCache(
                    key = key,
                    range = 0L..Long.MAX_VALUE,
                    download = entry.job,
                    channel = this,
                )
            }
        } finally {
            releaseForRead(entry)
        }
    }

    private suspend fun serveRange(
        key: String,
        range: RequestedRange,
        entry: Download,
        channel: ByteWriteChannel,
    ) {
        markActive(key)
        try {
            // 缓存已经覆盖这次请求：纯读本地，不碰网络
            if (range.endInclusive < cache.filledSize(key)) {
                streamFromCache(key, range.start..range.endInclusive, download = null, channel = channel)
                onCacheProgress(key)
                return
            }

            val served = streamFromCache(
                key = key,
                range = range.start..range.endInclusive,
                download = entry.job,
                channel = channel,
            )
            if (served < range.length) {
                logger.w {
                    "上游提前结束：key=$key 期望 ${range.length} 字节，实际 $served" +
                        "（下载任务${if (entry.job.isActive) "仍在进行" else "已结束"}）"
                }
            }
            onCacheProgress(key)
            evictIfNeeded()
        } finally {
            unmarkActive(key)
        }
    }

    /**
     * 等这次请求需要的第一个字节落盘（或确认上游已经失败）。
     *
     * 超时就按失败回给播放器：与其让它读一个迟迟不来数据的 206 然后超时，不如立刻给个明确的错误。
     */
    private suspend fun awaitRangeReady(key: String, start: Long, entry: Download): Boolean {
        val deadline = clock() + FIRST_BYTE_TIMEOUT_MILLIS
        while (true) {
            if (cache.filledSize(key) > start) return true
            if (!entry.job.isActive) return cache.filledSize(key) > start
            if (clock() >= deadline) {
                logger.w { "等上游首字节超时 key=$key start=$start" }
                return false
            }
            delay(READ_WAIT_POLL_MILLIS)
        }
    }

    /** 把上游的失败原因映射成播放器能理解的状态码。 */
    private fun upstreamStatusOf(error: Throwable?): HttpStatusCode = when {
        error is UpstreamStatusException && error.statusCode == 404 -> HttpStatusCode.NotFound
        error is UpstreamStatusException && error.statusCode in 401..403 -> HttpStatusCode.Forbidden
        else -> HttpStatusCode.BadGateway
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
