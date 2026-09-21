package com.lalilu.lmedia.stream

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
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
 * 网络媒体字节的本地缓存。与具体来源无关：WebDAV、Subsonic 或以后任何"能按区间取字节"
 * 的来源都能用同一个缓存，各来源以 [namespace] 区分目录。
 *
 * 采用**只追加的前缀模型**：`<key>.part` 里始终是从 0 开始的一段连续前缀，已缓存长度就等于文件
 * 长度本身。这样做的好处是不需要随机写，而且进程被杀掉也不会留下"记了却没写进去"的假覆盖——
 * 文件实际长度就是唯一真相。
 *
 * 代价：向前跳转播放时，要把中间没缓存的那段一起下完（单曲大小有界，且这段数据本来也会被缓存）。
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
) {

    companion object {
        /** 读写块大小；来源侧按块读取时也应使用同一粒度。 */
        const val CHUNK_SIZE = 64 * 1024
        private const val AUDIO_DIR = "audio"
        private const val AUDIO_SUFFIX = ".part"
        private const val USAGE_FILE = "usage.json"
    }

    init {
        require(namespace.isNotBlank()) { "Stream cache namespace must not be blank" }
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

    /** 已缓存的前缀长度。 */
    fun filledSize(key: String): Long =
        SystemFileSystem.metadataOrNull(audioPath(key))?.size ?: 0L

    /** 打开从 [start] 起始的读取源；文件不存在时返回 null。 */
    fun openSource(key: String, start: Long): Source? {
        val path = audioPath(key)
        if (!SystemFileSystem.exists(path)) return null
        val source = SystemFileSystem.source(path).buffered()
        if (start > 0L) source.skip(start)
        return source
    }

    /** 以追加方式打开写入端，调用方负责 flush 与 close。目录不存在时自动创建。 */
    fun openAppendSink(key: String): Sink {
        ensureReady()
        return SystemFileSystem.sink(audioPath(key), append = true).buffered()
    }

    fun appendBytes(key: String, bytes: ByteArray) {
        openAppendSink(key).use { sink -> sink.write(bytes) }
        // 记账必须跟上，否则淘汰时会按旧长度低估占用
        touch(key)
    }

    /** 读取一段字节，用于校验与后续的标签解析。 */
    fun readBytes(key: String, start: Long, length: Int): ByteArray? {
        if (length <= 0) return ByteArray(0)
        val source = openSource(key, start) ?: return null
        return source.use { it.readByteArray(length) }
    }

    /** 交给标签解析使用的本地路径；文件不存在或为空时返回 null。 */
    fun localPath(key: String): String? {
        val path = audioPath(key)
        if (!SystemFileSystem.exists(path)) return null
        if ((SystemFileSystem.metadataOrNull(path)?.size ?: 0L) <= 0L) return null
        return path.toString()
    }

    fun delete(key: String) {
        discardFile(key)
        loadUsage()
        usage.remove(key)
        usageDirty = true
    }

    /** 已缓存的所有缓存键。 */
    fun keys(): List<String> =
        runCatching {
            SystemFileSystem.list(audioDirectory)
                .map { it.name }
                .filter { it.endsWith(AUDIO_SUFFIX) }
                .map { it.removeSuffix(AUDIO_SUFFIX) }
        }.getOrDefault(emptyList())

    // ── 账目与淘汰 ──

    /** 记一次使用。[totalSize] 由远端声明，用于判断"是否已完整缓存"。 */
    fun touch(key: String, totalSize: Long? = null, at: Long = clock()) {
        loadUsage()
        val previous = usage[key]
        usage[key] = StreamCacheUsage(
            usedAt = at,
            bytes = filledSize(key),
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
            val bytes = record?.bytes?.takeIf { it > 0L }
                ?: (SystemFileSystem.metadataOrNull(audioPath(key))?.size ?: 0L)
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
                discardFile(entry.key)
                usage.remove(entry.key)
                usageDirty = true
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
     * 旧文件的尾巴，必须重建。返回 true 表示缓存仍然可用。
     */
    fun ensureConsistent(key: String, expectedTotal: Long): Boolean {
        if (expectedTotal <= 0L) return true
        val filled = filledSize(key)
        if (filled <= expectedTotal) return true
        discardFile(key)
        loadUsage()
        usage.remove(key)
        usageDirty = true
        return false
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

    private fun discardFile(key: String) {
        val path = audioPath(key)
        runCatching { if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path) }
    }

    private fun audioPath(key: String): Path = Path(audioDirectory, "$key$AUDIO_SUFFIX")
}
