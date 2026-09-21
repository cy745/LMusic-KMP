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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.io.readByteArray
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
 * **落盘规则**（见 [StreamCache] 的覆盖模型）：
 * - 请求正好接在连续前缀后面（顺序播放的常态）→ 继续往后接前缀。标签解析要的"从 0 开始的完整
 *   文件"因此始终成立。
 * - 其他情况（跳转）→ 按**整分段**落盘。于是播放器跳过的部分不会被顺手拖下来，只在真的播到时
 *   才取；分段的前半段若已在前缀里，从本地补进去，不重复跑网络。
 *
 * **服务规则**：请求范围内哪一段有缓存就读哪一段，读到空洞才去远端取，取的过程中顺带发给播放器。
 *
 * **后台补齐**（[notePlayhead] 触发，单 worker）：播放头及其前瞻最优先，其后从播放头顺序铺满，
 * 被播放器跳过的空洞最低（见 [nextFillRange]）。worker 每一步只补一个分段，且规划在 key 锁内
 * 进行——key 锁是公平的，排队中的播放器请求会先于 worker 的下一步被满足。
 */
@OptIn(ExperimentalTime::class)
class StreamProxy(
    private val cache: StreamCache,
    private val backend: StreamBackend,
    /** 每次缓存增长后回调，供上层判断 头部窗口 / 100% 提取节点与刷新缓冲进度。 */
    private val onCacheProgress: suspend (key: String) -> Unit = {},
    /** 音频缓存容量上限；<= 0 表示不限制。每次需要时读取，改配置立即生效。 */
    private val quotaBytes: () -> Long = { 0L },
    /** 淘汰检查的最小间隔：跳过它只为避免每次请求都扫一遍缓存目录。 */
    private val evictionIntervalMillis: Long = EVICTION_INTERVAL_MILLIS,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /**
     * 是否允许后台补齐整首（计费网络 / 开关关闭时为 false）。
     *
     * 只影响"用户还没要的字节"：播放头及其前瞻那一档永远做——那正是播放器接下来一定要的。
     */
    private val allowBackgroundFill: () -> Boolean = { true },
    /** 播放头前瞻窗口：这一档优先于"往后铺满"，避免慢链路上先铺远处再回头补眼前。 */
    private val lookaheadBytes: Long = DEFAULT_LOOKAHEAD_BYTES,
) {
    companion object {
        private const val TAG = "StreamProxy"
        internal const val LOOPBACK = "127.0.0.1"
        internal const val ROUTE_PREFIX = "/audio/"

        /** 淘汰检查间隔：容量是稳态约束而不是硬保证，5 秒粒度足够。 */
        internal const val EVICTION_INTERVAL_MILLIS = 5_000L

        /** 播放头前瞻窗口默认 4 MB：约一两分钟的音频，足够吸收网络抖动。 */
        const val DEFAULT_LOOKAHEAD_BYTES = 4L * 1024 * 1024
    }

    private val logger = Logger.withTag(TAG)
    private var server: EmbeddedServer<*, *>? = null
    private var baseUrl: String? = null
    private var scope: CoroutineScope? = null
    private val keyLocks = mutableMapOf<String, Mutex>()
    private val keyLocksGuard = Mutex()
    private var lastEvictionAt = 0L

    /** 正在被读取或写入的键：淘汰时绝不动它们。 */
    private val activeKeys = mutableSetOf<String>()

    /** 播放头提示：由播放器自己的 `Range` 请求暴露出来，不需要额外的状态管线。 */
    private val playheads = mutableMapOf<String, Long>()
    private val workers = mutableMapOf<String, Job>()
    private var activeKey: String? = null

    /** 补齐一步的结果：落盘多少字节、按序发给播放器多少字节。 */
    private data class FillOutcome(val stored: Long, val served: Long) {
        companion object {
            val NONE = FillOutcome(stored = 0L, served = 0L)
        }
    }

    /** 启动代理并返回基地址；重复调用返回同一地址。 */
    suspend fun start(): String {
        baseUrl?.let { return it }
        cache.ensureReady()

        val started = embeddedServer(CIO, port = 0, host = LOOPBACK) { proxyModule() }
        started.startSuspend(wait = false)
        val port = started.engine.resolvedConnectors().first().port
        server = started
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return "http://$LOOPBACK:$port".also { baseUrl = it }
    }

    suspend fun stop() {
        server?.stopSuspend()
        server = null
        baseUrl = null
        scope?.cancel()
        scope = null
        workers.clear()
        activeKey = null
        cache.persistUsage()
    }

    /** 播放地址；代理未启动时返回 null（调用方必须据此拒绝播放，不能回退到直链）。 */
    fun audioUrl(key: String): String? = baseUrl?.let { "$it$ROUTE_PREFIX$key" }

    /**
     * 告知"这个键正在被播放到 [position]"。
     *
     * 播放器的每个 `Range` 请求都会带上它当前的位置，所以这里不需要额外的播放状态管线。切歌时
     * 上一个 worker 会被取消——同一时刻只补正在播的那一首，否则快速切歌会留下好几个 worker
     * 一起抢带宽。
     */
    fun notePlayhead(key: String, position: Long) {
        playheads[key] = position
        if (key == activeKey && workers[key]?.isActive == true) return

        workers.values.forEach { it.cancel() }
        workers.clear()
        activeKey = key
        startFillWorker(key)
    }

    /**
     * 主动把缓存补到 [upTo]（含）——用于"允许后台主动下载"时补全尚未播放过的歌曲。
     *
     * 与播放请求共用同一把 key 锁，因此不会与正在进行的播放交错；每步只补一个分段，播放器的
     * 请求不会被长时间挡住。返回补齐后的已缓存字节数。
     */
    suspend fun ensureCached(
        key: String,
        target: StreamTarget,
        upTo: Long,
    ): Long {
        if (target.totalSize <= 0L) return cache.coveredBytes(key)
        val last = minOf(upTo, target.totalSize - 1L).takeIf { it >= 0L }
            ?: return cache.coveredBytes(key)

        while (currentCoroutineContext().isActive) {
            val progressed = withActiveKey(key) { fillFirstHole(key, last) }
            if (!progressed) break
            onCacheProgress(key)
            evictIfNeeded()
            yield()
        }
        return cache.coveredBytes(key, target.totalSize)
    }

    /** 当前是否有 worker 在补这个键（诊断与测试用）。 */
    internal fun isFilling(key: String): Boolean = workers[key]?.isActive == true

    // ── 后台补齐 ──

    private fun startFillWorker(key: String) {
        val scope = scope ?: return
        workers[key] = scope.launch {
            try {
                fillLoop(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                logger.w(messageString = "后台补齐中断：key=$key ${throwable.message}")
            }
        }
    }

    private suspend fun fillLoop(key: String) {
        while (currentCoroutineContext().isActive) {
            // 规划放在锁内：等锁期间播放器可能已经把这段补上了，锁外的计划会作废
            val progressed = withActiveKey(key) { fillPriorityStep(key) }
            if (!progressed) return
            onCacheProgress(key)
            evictIfNeeded()
            // 让出：排在锁上的播放器请求优先于 worker 的下一步
            yield()
        }
    }

    /** 按优先级补一步（锁内调用）。返回是否真的补到了字节。 */
    private suspend fun fillPriorityStep(key: String): Boolean {
        val target = backend.resolve(key) ?: return false
        if (target.totalSize <= 0L) return false

        val next = nextFillRange(
            coverage = cache.coveredRanges(key, target.totalSize),
            totalSize = target.totalSize,
            playhead = playheads[key] ?: 0L,
            lookaheadBytes = lookaheadBytes,
            allowBackground = allowBackgroundFill(),
        ) ?: return false

        return fillRange(
            key = key,
            target = target,
            from = next.start,
            toInclusive = minOf(next.endInclusive, stepEnd(next.start, target.totalSize)),
            serveFrom = 0L,
            serveTo = -1L,
            channel = null,
        ).stored > 0L
    }

    /** 从 0 开始补第一个空洞（锁内调用）。返回是否真的补到了字节。 */
    private suspend fun fillFirstHole(key: String, upTo: Long): Boolean {
        val target = backend.resolve(key) ?: return false
        if (target.totalSize <= 0L) return false

        val last = minOf(upTo, target.totalSize - 1L)
        val next = cache.missingRanges(key, 0L, last, target.totalSize).firstOrNull() ?: return false

        return fillRange(
            key = key,
            target = target,
            from = next.start,
            toInclusive = minOf(next.endInclusive, stepEnd(next.start, target.totalSize)),
            serveFrom = 0L,
            serveTo = -1L,
            channel = null,
        ).stored > 0L
    }

    // ── 落盘 ──

    /**
     * 把 `[from, toInclusive]` 补进缓存，并把落在 `[serveFrom, serveTo]` 的字节按序交给 [channel]。
     *
     * 只有"正好接在连续前缀后面"才继续接前缀；其余一律按整分段落盘——分段必须写满才能提交
     * （可见性以文件存在为准），因此跳转点落在某分段中间时会把这一段的前面部分一起取下来，
     * 上限一个分段，代价是换来"不为跳过的部分付优先级"。
     */
    private suspend fun fillRange(
        key: String,
        target: StreamTarget,
        from: Long,
        toInclusive: Long,
        serveFrom: Long,
        serveTo: Long,
        channel: ByteWriteChannel?,
    ): FillOutcome {
        val total = target.totalSize
        val last = if (total > 0L) minOf(toInclusive, total - 1L) else toInclusive
        if (last < from) return FillOutcome.NONE

        // 远端换成更小的文件时旧前缀比新文件还长：先重建，避免把旧文件的尾巴当成有效缓存
        cache.ensureConsistent(key, total)

        return if (from == cache.prefixSize(key)) {
            appendToPrefix(key, target, from, last, serveFrom, serveTo, channel)
        } else {
            writeSegments(key, target, from, last, serveFrom, serveTo, channel)
        }
    }

    /** 接着连续前缀往后写。顺序播放走这里，前缀因此始终是"从 0 开始的完整文件"。 */
    private suspend fun appendToPrefix(
        key: String,
        target: StreamTarget,
        from: Long,
        last: Long,
        serveFrom: Long,
        serveTo: Long,
        channel: ByteWriteChannel?,
    ): FillOutcome {
        val prefix = cache.prefixSize(key)
        val sink = cache.openAppendSink(key)
        var stored = 0L
        var served = 0L
        try {
            backend.fetch(target, prefix, if (target.totalSize > 0L) last else null) { bytes ->
                // 先落盘：即使随后客户端断开，已收到的字节依然是有效的前缀
                sink.write(bytes)
                val absolute = prefix + stored
                stored += bytes.size
                served += forward(bytes, 0, absolute, serveFrom, serveTo, channel)
            }
            sink.flush()
        } finally {
            sink.close()
        }

        cache.touch(key, target.totalSize)
        return FillOutcome(stored = stored, served = served)
    }

    /** 跳转之后按整分段写：写满才提交（原子改名），因此"文件存在"就等于"这一段完整可用"。 */
    private suspend fun writeSegments(
        key: String,
        target: StreamTarget,
        from: Long,
        last: Long,
        serveFrom: Long,
        serveTo: Long,
        channel: ByteWriteChannel?,
    ): FillOutcome {
        val total = target.totalSize
        val segmentSize = cache.segmentSize
        var position = from
        var stored = 0L
        var served = 0L

        while (position <= last) {
            val index = (position / segmentSize).toInt()
            val segmentStart = index.toLong() * segmentSize
            val segmentLast = if (total > 0L) {
                minOf(segmentStart + segmentSize - 1L, total - 1L)
            } else {
                segmentStart + segmentSize - 1L
            }
            val expected = segmentLast - segmentStart + 1L

            val writer = cache.openSegmentWriter(key, index, expected)
            var fetched = 0L
            try {
                // 分段开头可能已经落在连续前缀里：从本地补，不为已有的字节再跑一趟网络
                fetched += copyFromPrefix(key, segmentStart, expected, writer)
                if (fetched < expected) {
                    backend.fetch(target, segmentStart + fetched, segmentLast) { bytes ->
                        writer.write(bytes)
                        val absolute = segmentStart + fetched
                        fetched += bytes.size
                        served += forward(bytes, 0, absolute, serveFrom, serveTo, channel)
                    }
                }
            } catch (throwable: Throwable) {
                writer.abort()
                throw throwable
            }

            if (fetched < expected) {
                // 上游提前结束：半个分段不能当"完整可用"提交，否则缺的字节会被算成已缓存
                writer.abort()
                logger.w(
                    messageString = "上游提前结束：key=$key 分段 $index 期望 $expected 字节，实际 $fetched",
                )
                cache.touch(key, target.totalSize)
                return FillOutcome(stored = stored, served = served)
            }

            writer.commit()
            stored += fetched
            position = segmentLast + 1L
        }

        cache.touch(key, target.totalSize)
        return FillOutcome(stored = stored, served = served)
    }

    /**
     * 分段开头已经在前缀里时，从本地把它补进分段，返回补进去的字节数。
     *
     * 这一段字节已经在磁盘上了，再跑一趟网络纯属浪费——尤其是它恰好是播放器跳过的那部分。
     */
    private fun copyFromPrefix(
        key: String,
        segmentStart: Long,
        maxBytes: Long,
        writer: SegmentWriter,
    ): Long {
        val prefix = cache.prefixSize(key)
        if (segmentStart >= prefix) return 0L

        val available = minOf(prefix - segmentStart, maxBytes)
        val source = cache.openSource(key, segmentStart) ?: return 0L
        return source.use { src ->
            var copied = 0L
            while (copied < available) {
                val want = minOf(StreamCache.CHUNK_SIZE.toLong(), available - copied).toInt()
                val chunk = src.readByteArray(want)
                if (chunk.isEmpty()) break
                writer.write(chunk)
                copied += chunk.size
            }
            copied
        }
    }

    /**
     * 把 [bytes] 里落在 `[serveFrom, serveTo]` 的切片写进 [channel]，返回写出的字节数。
     *
     * [absolute] 是这段字节在文件里的偏移。切片只发生窗口边界处，因此只有边界那几个块需要拷贝。
     */
    private suspend fun forward(
        bytes: ByteArray,
        offset: Int,
        absolute: Long,
        serveFrom: Long,
        serveTo: Long,
        channel: ByteWriteChannel?,
    ): Long {
        if (channel == null || serveTo < serveFrom) return 0L

        val start = maxOf(absolute, serveFrom)
        val end = minOf(absolute + bytes.size - 1L, serveTo)
        if (end < start) return 0L

        val from = maxOf((start - absolute).toInt(), offset)
        val to = (end - absolute).toInt() + 1
        if (from <= 0 && to == bytes.size) {
            channel.writeFully(bytes, 0, bytes.size)
        } else {
            channel.writeFully(bytes.copyOfRange(from, to))
        }
        return to - from.toLong()
    }

    /** [position] 所在分段的末尾（受总长限制）——后台单步补齐的粒度上限。 */
    private fun stepEnd(position: Long, totalSize: Long): Long {
        val segmentSize = cache.segmentSize
        val segmentEnd = (position / segmentSize) * segmentSize + segmentSize - 1L
        return if (totalSize > 0L) minOf(segmentEnd, totalSize - 1L) else segmentEnd
    }

    // ── 淘汰 ──

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

    // ── HTTP ──

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

        // 播放器的这个请求本身就说明了它播到哪儿了：据此调整后台补齐的优先级
        notePlayhead(key, range.start)

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
            withActiveKey(key) {
                cache.touch(key, target.totalSize)
                val cached = cache.prefixSize(key)
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
            }
        }
    }

    /** 按覆盖服务：缓存里有就读缓存，读到空洞就边取边发。 */
    private suspend fun serveRange(
        key: String,
        target: StreamTarget,
        range: ByteRange,
        channel: ByteWriteChannel,
    ) {
        withActiveKey(key) {
            var position = range.start
            while (position <= range.endInclusive) {
                val span = cache.openSpan(
                    key = key,
                    start = position,
                    maxLength = range.endInclusive - position + 1L,
                    totalSize = target.totalSize,
                )
                if (span != null) {
                    span.source.use { source -> channel.writeBuffer(source, span.length) }
                    position += span.length
                    continue
                }

                val outcome = fillRange(
                    key = key,
                    target = target,
                    from = position,
                    toInclusive = range.endInclusive,
                    serveFrom = position,
                    serveTo = range.endInclusive,
                    channel = channel,
                )
                // 取不到新字节就停下：再循环只会把同一件事重试一遍
                if (outcome.served <= 0L) {
                    logger.w(messageString = "上游提前结束：key=$key 位置 $position 起没取到字节")
                    break
                }
                position += outcome.served
            }

            cache.touch(key, target.totalSize)
            onCacheProgress(key)
            evictIfNeeded()
        }
    }

    private suspend fun <T> withActiveKey(key: String, block: suspend () -> T): T = lockFor(key).withLock {
        activeKeys += key
        try {
            block()
        } finally {
            activeKeys -= key
        }
    }

    private suspend fun lockFor(key: String): Mutex = keyLocksGuard.withLock {
        keyLocks.getOrPut(key) { Mutex() }
    }
}
