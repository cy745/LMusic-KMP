package com.lalilu.lmedia.stream

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 一条缓存的账目：文件本身是唯一真相，这里只记"多久没用过"、有多大、期望多大。 */
@Serializable
data class StreamCacheUsage(
    /** 最后一次使用（播放读取或写入）的时间戳，用于 LRU。 */
    val usedAt: Long = 0L,
    /** 最后一次记账时的已缓存长度；用于免去每次淘汰都 stat 每个文件。 */
    val bytes: Long = 0L,
    /** 远端声明的总长度；0 表示未知（此时无法判断是否完整）。 */
    val totalSize: Long = 0L,
)

/** 缓存里的一条记录（账目 + 实际磁盘长度）。 */
data class StreamCacheEntry(
    val key: String,
    val bytes: Long,
    val usedAt: Long,
    val totalSize: Long,
) {
    /** 已完整缓存：只有知道期望长度才敢这么说。 */
    val isComplete: Boolean get() = totalSize > 0L && bytes >= totalSize
}

/**
 * 从缓存里读出的一段连续字节。[length] 是这一段实际可读的长度（可能短于请求长度），
 * [source] 用完必须 close。
 */
class CachedSpan internal constructor(
    val start: Long,
    val length: Long,
    val source: Source,
)

/**
 * 网络媒体字节的本地缓存。与具体来源无关：WebDAV、Subsonic 或以后任何"能按区间取字节"
 * 的来源都能用同一个缓存，各来源以 [namespace] 区分目录。
 *
 * 存储分两种，合起来构成**区间覆盖**（而不是只支持"从 0 开始的连续前缀"）：
 *
 * - `<key>.part`：从 0 开始的连续前缀。顺序播放天然走这里，标签解析要的"从 0 开始的完整文件"
 *   也只有它满足。
 * - `<key>/seg.<i>`：固定大小分段，覆盖 `[i*segmentSize, (i+1)*segmentSize)`。播放器跳转后
 *   产生的"空洞之后"的数据写这里，于是跳过去的那段不必跟着一起下。
 *
 * 分段先写 `<key>/seg.<i>.tmp.<random>` 再原子改名，因此**"文件存在"就等于"这一段完整可用"**：
 * 崩溃只会留下可忽略的临时文件，不会留下"记了却没写全"的假覆盖。这也是不做随机写
 * （kotlinx-io 的 sink 只支持追加，随机写要每个平台各写一遍 `expect/actual`）就能乱序缓存的原因。
 *
 * 另有一份 `usage.json` 账目（最近使用时间 + 远端总长），用于容量上限下的 LRU 淘汰；账目丢失
 * 只会让淘汰顺序退化到"按文件修改时间"，不影响正确性。
 */
@OptIn(ExperimentalTime::class)
class StreamCache(
    cacheRoot: String,
    /** 来源命名空间（例如 `webdav`）：不同来源的缓存互不干扰，也保证已有缓存目录不失效。 */
    namespace: String,
    private val json: Json,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** 分段大小；测试里会调小，以便用少量字节覆盖边界。 */
    val segmentSize: Long = SEGMENT_SIZE,
) {

    companion object {
        /** 读写块大小；来源侧按块读取时也应使用同一粒度。 */
        const val CHUNK_SIZE = 64 * 1024

        /** 分段大小：与头部窗口同量级——太小文件数暴涨，太大"补空洞"的粒度又太粗。 */
        const val SEGMENT_SIZE = 1L * 1024 * 1024

        private const val AUDIO_DIR = "audio"
        private const val AUDIO_SUFFIX = ".part"
        private const val SEGMENT_PREFIX = "seg."
        private const val SEGMENT_TEMP_MARK = ".tmp."
        private const val USAGE_FILE = "usage.json"
    }

    init {
        require(namespace.isNotBlank()) { "Stream cache namespace must not be blank" }
        require(segmentSize > 0L) { "Segment size must be positive: $segmentSize" }
    }

    private val root: Path = Path(cacheRoot, "lmedia", namespace)
    private val audioDirectory: Path = Path(root, AUDIO_DIR)
    private val usagePath: Path = Path(root, USAGE_FILE)

    private val usage = mutableMapOf<String, StreamCacheUsage>()
    private var usageLoaded = false
    private var usageDirty = false

    fun ensureReady() {
        SystemFileSystem.createDirectories(audioDirectory, mustCreate = false)
    }

    // ── 首段连续前缀 ──

    /** 从 0 开始连续缓存的长度。标签解析与"缓存是否一致"都只认这一段。 */
    fun prefixSize(key: String): Long =
        SystemFileSystem.metadataOrNull(audioPath(key))?.size ?: 0L

    /** 打开从 [start] 起始的前缀读取源；文件不存在时返回 null。 */
    fun openSource(key: String, start: Long): Source? {
        val path = audioPath(key)
        if (!SystemFileSystem.exists(path)) return null
        val source = SystemFileSystem.source(path).buffered()
        if (start > 0L) source.skip(start)
        return source
    }

    /** 以追加方式打开前缀写入端，调用方负责 flush 与 close。目录不存在时自动创建。 */
    fun openAppendSink(key: String): Sink {
        ensureReady()
        return SystemFileSystem.sink(audioPath(key), append = true).buffered()
    }

    fun appendBytes(key: String, bytes: ByteArray) {
        openAppendSink(key).use { sink -> sink.write(bytes) }
        // 记账必须跟上，否则淘汰时会按旧长度低估占用
        touch(key)
    }

    /** 交给标签解析使用的本地路径——只有连续前缀能当"从 0 开始的完整文件"用。 */
    fun localPath(key: String): String? {
        val path = audioPath(key)
        if (!SystemFileSystem.exists(path)) return null
        if ((SystemFileSystem.metadataOrNull(path)?.size ?: 0L) <= 0L) return null
        return path.toString()
    }

    // ── 分段 ──

    /** 某个键已存在的分段索引与长度；临时文件不算。 */
    fun segments(key: String): Map<Int, Long> {
        val directory = segmentDirectory(key)
        if (!SystemFileSystem.exists(directory)) return emptyMap()
        return runCatching {
            SystemFileSystem.list(directory)
                .mapNotNull { path ->
                    segmentIndexOrNull(path.name)?.let { index -> index to fileSize(path) }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    /**
     * 打开一个分段写入器：数据写临时文件，[SegmentWriter.commit] 时原子改名。
     *
     * [length] 是这一段的预期长度（远端总长不是分段整数倍时最后一段会短一些）；实际写入多少由
     * 调用方决定，commit 之后以实际长度为准。
     */
    fun openSegmentWriter(key: String, index: Int, length: Long): SegmentWriter {
        require(index >= 0) { "Segment index must not be negative: $index" }
        require(length > 0L) { "Segment length must be positive: $length" }
        val directory = segmentDirectory(key)
        SystemFileSystem.createDirectories(directory, mustCreate = false)
        val tempPath = Path(directory, "$SEGMENT_PREFIX$index$SEGMENT_TEMP_MARK${Random.nextLong()}")
        return SegmentWriter(
            tempPath = tempPath,
            finalPath = Path(directory, "$SEGMENT_PREFIX$index"),
        )
    }

    fun deleteSegment(key: String, index: Int) {
        runCatching {
            val path = segmentPath(key, index)
            if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path)
        }
    }

    /** 落后于连续前缀的分段：字节已经在前缀里，留着只会重复占用配额。 */
    fun discardSegmentsWithinPrefix(key: String) {
        val prefix = prefixSize(key)
        if (prefix <= 0L) return
        segments(key).keys.forEach { index ->
            if ((index + 1).toLong() * segmentSize <= prefix) deleteSegment(key, index)
        }
    }

    // ── 区间覆盖 ──

    /**
     * 已缓存的区间（有序、互不相邻）。已知 [totalSize] 时会裁掉越界的部分——远端文件变小后，
     * 分段里可能留着旧文件的尾巴，不能算作有效覆盖。
     */
    fun coveredRanges(key: String, totalSize: Long = 0L): List<CachedRange> {
        val raw = mutableListOf<CachedRange>()
        val prefix = prefixSize(key)
        if (prefix > 0L) raw += CachedRange(0L, prefix - 1L)
        segments(key).forEach { (index, length) ->
            if (length <= 0L) return@forEach
            val start = index.toLong() * segmentSize
            raw += CachedRange(start, start + length - 1L)
        }

        val normalized = normalizeCoverage(raw)
        if (totalSize <= 0L) return normalized
        return normalizeCoverage(
            normalized.mapNotNull { range ->
                if (range.start >= totalSize) null
                else CachedRange(range.start, minOf(range.endInclusive, totalSize - 1L))
            },
        )
    }

    /** 已缓存的总字节数（前缀 + 分段）。缓冲进度显示用的就是它。 */
    fun coveredBytes(key: String, totalSize: Long = 0L): Long =
        coverageBytes(coveredRanges(key, totalSize))

    /** `[from, toInclusive]` 是否都已缓存。 */
    fun isCovered(key: String, from: Long, toInclusive: Long, totalSize: Long = 0L): Boolean =
        isCoverageComplete(coveredRanges(key, totalSize), from, toInclusive)

    /** `[from, toInclusive]` 里尚未缓存的空洞，供调度器决定下一段下哪里。 */
    fun missingRanges(key: String, from: Long, toInclusive: Long, totalSize: Long = 0L): List<CachedRange> =
        missingCoverage(coveredRanges(key, totalSize), from, toInclusive)

    /**
     * 从 [start] 起打开一段连续可读的缓存；该位置没有缓存时返回 null。
     *
     * 返回长度可能短于 [maxLength]（跨到空洞或跨到别的分段），调用方必须按返回长度推进。
     */
    fun openSpan(
        key: String,
        start: Long,
        maxLength: Long,
        totalSize: Long = 0L,
    ): CachedSpan? {
        if (start < 0L || maxLength <= 0L) return null
        if (totalSize > 0L && start >= totalSize) return null

        val prefix = prefixSize(key)
        if (start < prefix) {
            val source = openSource(key, start) ?: return null
            return CachedSpan(
                start = start,
                length = minOf(prefix - start, maxLength),
                source = source,
            )
        }

        val index = (start / segmentSize).toInt()
        val offset = start % segmentSize
        val length = segments(key)[index] ?: return null
        if (offset >= length) return null

        val path = segmentPath(key, index)
        if (!SystemFileSystem.exists(path)) return null
        val source = SystemFileSystem.source(path).buffered()
        if (offset > 0L) source.skip(offset)
        return CachedSpan(
            start = start,
            length = minOf(length - offset, maxLength),
            source = source,
        )
    }

    /** 读取一段字节，用于校验与标签解析；该范围没有缓存时返回 null。 */
    fun readBytes(key: String, start: Long, length: Int): ByteArray? {
        if (length <= 0) return ByteArray(0)
        val span = openSpan(key, start, length.toLong()) ?: return null
        return span.source.use { it.readByteArray(minOf(span.length, length.toLong()).toInt()) }
    }

    // ── 键与占用 ──

    fun delete(key: String) {
        discardFile(key)
        discardSegments(key)
        loadUsage()
        usage.remove(key)
        usageDirty = true
    }

    /** 已缓存的所有缓存键（含只有分段的键）。 */
    fun keys(): List<String> =
        runCatching {
            val keys = mutableSetOf<String>()
            SystemFileSystem.list(audioDirectory).forEach { path ->
                val isDirectory = SystemFileSystem.metadataOrNull(path)?.isDirectory == true
                when {
                    isDirectory -> keys += path.name
                    path.name.endsWith(AUDIO_SUFFIX) -> keys += path.name.removeSuffix(AUDIO_SUFFIX)
                }
            }
            keys.toList()
        }.getOrDefault(emptyList())

    // ── 账目与淘汰 ──

    /** 记一次使用。[totalSize] 由远端声明，用于判断"是否已完整缓存"。 */
    fun touch(key: String, totalSize: Long? = null, at: Long = clock()) {
        loadUsage()
        val previous = usage[key]
        usage[key] = StreamCacheUsage(
            usedAt = at,
            bytes = diskBytes(key),
            totalSize = when {
                totalSize != null && totalSize > 0L -> totalSize
                previous != null && previous.totalSize > 0L -> previous.totalSize
                else -> 0L
            },
        )
        usageDirty = true
    }

    /**
     * 当前缓存账目。
     *
     * 已记账的文件直接用账目里的长度（避免每次淘汰都 stat 成千上万个文件），只有磁盘上存在但
     * 没记账的文件（账目丢失/被外部写入）才去 stat 一次。
     */
    fun entries(): List<StreamCacheEntry> {
        loadUsage()
        return keys().map { key ->
            val record = usage[key]
            val bytes = record?.bytes?.takeIf { it > 0L } ?: diskBytes(key)
            StreamCacheEntry(
                key = key,
                bytes = bytes,
                usedAt = record?.usedAt ?: 0L,
                totalSize = record?.totalSize ?: 0L,
            )
        }
    }

    fun usedBytes(): Long = entries().sumOf { it.bytes }

    /**
     * 超出容量上限时按 LRU 淘汰，返回被删掉的缓存键。
     *
     * 淘汰顺序：**部分缓存优先**（`isComplete == false`），同类里再按最久未使用排序。
     * 理由是完整缓存的重播收益最大，而部分缓存只是"听过一小段"的副产品，重新下载的代价也最小。
     * [pinned] 里的键（正在播放）永远不动，否则会把用户正在听的歌删掉。
     */
    fun evictIfNeeded(
        quotaBytes: Long,
        pinned: Set<String> = emptySet(),
        preserveHeadroom: Double = 0.9,
    ): List<String> {
        if (quotaBytes <= 0L) return emptyList()
        val all = entries()
        val used = all.sumOf { it.bytes }
        if (used <= quotaBytes) return emptyList()

        // 留一点余量，避免刚淘汰完又立刻越界（否则每次写入都要扫一遍淘汰）
        val target = (quotaBytes * preserveHeadroom).toLong().coerceAtLeast(0L)
        val evicted = mutableListOf<String>()
        var remaining = used
        all.asSequence()
            .filterNot { it.key in pinned }
            .sortedWith(compareBy({ it.isComplete }, { it.usedAt }))
            .forEach { entry ->
                if (remaining <= target) return@forEach
                delete(entry.key)
                remaining -= entry.bytes
                evicted += entry.key
            }
        if (evicted.isNotEmpty()) flushUsage()
        return evicted
    }

    /**
     * 缓存与远端声明长度不一致时丢弃重来。
     *
     * 远端文件被替换成更小的文件后，已缓存的前缀可能比新文件还长——继续当作有效前缀会读到
     * 旧文件的尾巴，必须重建；越界的分段同样要清掉。返回 true 表示缓存仍然可用。
     */
    fun ensureConsistent(key: String, expectedTotal: Long): Boolean {
        if (expectedTotal <= 0L) return true

        var usable = true
        if (prefixSize(key) > expectedTotal) {
            discardFile(key)
            loadUsage()
            usage.remove(key)
            usageDirty = true
            usable = false
        }

        segments(key).keys.forEach { index ->
            if (index.toLong() * segmentSize >= expectedTotal) deleteSegment(key, index)
        }
        return usable
    }

    /** 把账目写盘；进程内多次使用时由调用方在合适时机调用。 */
    fun persistUsage() {
        if (!usageDirty) return
        flushUsage()
    }

    private fun flushUsage() {
        ensureReady()
        usageDirty = false
        runCatching {
            val text = json.encodeToString(usage.filterValues { it.usedAt > 0L || it.totalSize > 0L })
            SystemFileSystem.sink(usagePath).buffered().use { it.writeString(text) }
        }.onFailure { usageDirty = true }
    }

    private fun loadUsage() {
        if (usageLoaded) return
        usageLoaded = true
        if (!SystemFileSystem.exists(usagePath)) return
        runCatching {
            val text = SystemFileSystem.source(usagePath).buffered().use { it.readString() }
            usage.putAll(json.decodeFromString<Map<String, StreamCacheUsage>>(text))
        }
    }

    private fun diskBytes(key: String): Long =
        prefixSize(key) + segments(key).values.sum()

    private fun discardFile(key: String) {
        val path = audioPath(key)
        runCatching { if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path) }
    }

    private fun discardSegments(key: String) {
        val directory = segmentDirectory(key)
        if (!SystemFileSystem.exists(directory)) return
        runCatching {
            SystemFileSystem.list(directory).forEach { SystemFileSystem.delete(it, mustExist = false) }
            SystemFileSystem.delete(directory, mustExist = false)
        }
    }

    private fun fileSize(path: Path): Long = SystemFileSystem.metadataOrNull(path)?.size ?: 0L

    private fun segmentIndexOrNull(name: String): Int? {
        if (name.contains(SEGMENT_TEMP_MARK)) return null
        return name.removePrefix(SEGMENT_PREFIX).toIntOrNull()
    }

    private fun audioPath(key: String): Path = Path(audioDirectory, "$key$AUDIO_SUFFIX")

    private fun segmentDirectory(key: String): Path = Path(audioDirectory, key)

    private fun segmentPath(key: String, index: Int): Path =
        Path(segmentDirectory(key), "$SEGMENT_PREFIX$index")
}

/**
 * 分段写入器：数据先写临时文件，[commit] 时原子改名。
 *
 * 因此"分段文件存在"就等于"这一段完整可用"——崩溃只会留下一个可忽略的临时文件。
 */
class SegmentWriter internal constructor(
    private val tempPath: Path,
    private val finalPath: Path,
) {
    private val sink: Sink = SystemFileSystem.sink(tempPath).buffered()
    private var closed = false

    fun write(bytes: ByteArray) {
        check(!closed) { "Segment writer is already closed" }
        sink.write(bytes)
    }

    /** 落盘并原子改名；失败（例如同一分段已被并发写入）时清理临时文件并返回 false。 */
    fun commit(): Boolean {
        if (closed) return false
        closed = true
        return runCatching {
            sink.flush()
            sink.close()
            SystemFileSystem.atomicMove(tempPath, finalPath)
            true
        }.getOrElse {
            runCatching { sink.close() }
            cleanup()
            false
        }
    }

    fun abort() {
        if (closed) return
        closed = true
        runCatching { sink.close() }
        cleanup()
    }

    private fun cleanup() {
        runCatching {
            if (SystemFileSystem.exists(tempPath)) SystemFileSystem.delete(tempPath)
        }
    }
}
