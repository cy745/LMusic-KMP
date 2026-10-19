package com.lalilu.lmedia.source.webdav

import com.lalilu.lmedia.cache.MediaCacheStore
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 提取进度，供源卡片展示。 */
data class WebDavExtractionState(
    val total: Int = 0,
    val extracted: Int = 0,
    val running: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
) {
    val isIdle: Boolean get() = running == 0 && pending == 0
}

/** 提取决策。 */
internal enum class ExtractionDecision { SKIP, PARTIAL, FULL }

/**
 * 提取节点：越往后数据越全。
 *
 * 顺序按"信息量"排：1MB 前缀（元数据块 + 内嵌封面）→ 30%（还是同一批字段，但数据更全）→ 完成
 * （可信时长）。同一首歌不允许在同一个节点上重复解析，只有推进到下一个节点才值得再读一次。
 */
internal enum class ExtractionNode { PREFIX_HEAD, PARTIAL, FULL }

/** 30% 阈值：这一节点上仍然只有"部分文件"，但元数据块早就齐了。 */
internal const val PARTIAL_EXTRACTION_THRESHOLD = 0.3

/** 1MB 前缀足以覆盖 FLAC 的 STREAMINFO/PICTURE/VORBIS_COMMENT 与 MP3 的 ID3v2（含内嵌封面）。 */
internal const val HEAD_EXTRACTION_BYTES = 1L * 1024 * 1024

/**
 * 当前缓存长度落在哪个节点上；还没到任何节点返回 null。
 *
 * 3.4MB 以上的文件会先到 1MB 节点、再到 30%、最后到完成；小文件可能直接跳到 30% 或完成——
 * 节点只用来决定"这次值不值得解析"，具体读什么由 [ExtractionDecision] 决定。
 */
internal fun extractionNodeOf(filled: Long, total: Long): ExtractionNode? {
    if (total > 0L) {
        if (filled >= total) return ExtractionNode.FULL
        if (filled >= (total * PARTIAL_EXTRACTION_THRESHOLD).toLong()) return ExtractionNode.PARTIAL
        if (filled >= HEAD_EXTRACTION_BYTES) return ExtractionNode.PREFIX_HEAD
        return null
    }
    // 服务器没给长度：只要有字节就先读一次文件头，剩下的等它结束时再说
    return if (filled > 0L) ExtractionNode.PREFIX_HEAD else null
}

/**
 * 判断这一次缓存增长值不值得读一次标签。
 *
 * - 1MB 节点：文件头的元数据块已经完整，标题/歌手/专辑/内嵌封面都能拿到——这是最快的一次；
 * - 30% 节点：只在 1MB 那次**没读到有效数据**时补一次（有的文件元数据块更靠后）；
 * - 完成节点：补齐可信时长（部分文件里时长只能靠比特率估），记录不完整就再读一次；
 * - 指纹变化（远端文件被替换）时缓存记录失效，允许重新提取；
 * - 同一个节点上试过就跳过，避免缓存每长 1MB 就重读一遍标签。
 */
internal fun decideExtraction(
    node: ExtractionNode,
    existing: WebDavMetadataRecord?,
    currentFingerprint: String,
    lastAttemptedNode: ExtractionNode? = null,
): ExtractionDecision {
    if (lastAttemptedNode != null && node.ordinal <= lastAttemptedNode.ordinal) {
        return ExtractionDecision.SKIP
    }

    val stale = existing == null || existing.fingerprint != currentFingerprint
    if (stale) return if (node == ExtractionNode.FULL) ExtractionDecision.FULL else ExtractionDecision.PARTIAL

    if (node == ExtractionNode.FULL) {
        // 已经完整就直接用；不完整（缺时长）才再读一次
        return if (existing.complete) ExtractionDecision.SKIP else ExtractionDecision.FULL
    }

    // 1MB / 30%：上一次已经把标题之类的读出来了就不用重复读
    return if (existing.hasUsefulMetadata()) ExtractionDecision.SKIP else ExtractionDecision.PARTIAL
}

/** 记录里有没有"读到了标签"的证据；没有就说明上一次解析没拿到东西，下个节点要再试。 */
internal fun WebDavMetadataRecord.hasUsefulMetadata(): Boolean =
    title.isNotBlank() || artist.isNotBlank() || album.isNotBlank() || hasCover

/** 提取某个缓存键所需的信息：目标歌曲、远端路径与指纹。 */
internal data class WebDavExtractionTarget(
    val audio: LAudio,
    val remotePath: String,
    val totalSize: Long,
    val fingerprint: String,
)

internal interface WebDavExtractionTargets {
    /** 歌曲已不在当前快照中时返回 null。 */
    suspend fun targetOf(key: String): WebDavExtractionTarget?
}

/**
 * 提取编排：缓存增长 → 决策 → 读标签 → 写本地元数据 → 逐条入库 + 批量合并回快照。
 *
 * 三个节点（1MB 前缀 / 30% / 完成）由播放代理的进度上报触发，因此**只有听过的歌会被补全**；
 * [request] 也用于"允许后台主动下载"时对未播放歌曲的补全。1MB 那次是为了"尽量早看到标题与封面"：
 * FLAC 的 STREAMINFO/PICTURE 与 MP3 的 ID3v2 都在文件头，1MB 足够；万一那一次什么都没读到
 * （元数据块更靠后、或者文件头还没下完整），30% 会再补一次；完成时再补上可信时长。
 */
@OptIn(ExperimentalTime::class)
internal class WebDavExtractor(
    private val cache: MediaCacheStore,
    private val store: WebDavMetadataStore,
    private val targets: WebDavExtractionTargets,
    private val progressState: MutableStateFlow<WebDavExtractionState>,
    private val onRecord: suspend (WebDavExtractionTarget, WebDavMetadataRecord) -> Unit,
    private val onFlush: suspend (Map<String, WebDavMetadataRecord>) -> Unit,
    private val concurrency: Int = 2,
    private val batchSize: Int = BATCH_SIZE,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    tagReaderOverride: (suspend (path: String, includeCover: Boolean, includeDuration: Boolean) -> WebDavExtractedMetadata?)? = null,
) {
    companion object {
        private const val TAG = "WebDavExtractor"

        /** 每累积这么多条提取结果，就把它们合并回完整快照一次。 */
        internal const val BATCH_SIZE = 20
    }

    private val logger = Logger.withTag(TAG)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val pendingMerges = mutableMapOf<String, WebDavMetadataRecord>()
    private val mergeMutex = Mutex()
    private val pendingCount = MutableStateFlow(0)

    /** 已经补发过补丁的键（同一个提取器实例内不再重复）。 */
    private val republishedKeys = mutableSetOf<String>()

    /** 每个键上一次解析发生在哪个节点、针对哪个指纹；同节点不重复解析。 */
    private val attemptedNodes = mutableMapOf<String, Pair<ExtractionNode, String>>()
    private var worker: Job? = null

    /** 标签读取做成可替换的依赖：默认走 taglib，测试可以注入确定性结果。 */
    private val tagReader: suspend (path: String, includeCover: Boolean, includeDuration: Boolean) -> WebDavExtractedMetadata? =
        tagReaderOverride ?: ::readTagsFromFile

    val progress: StateFlow<WebDavExtractionState> = progressState.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (worker != null) return
        worker = scope.launch {
            coroutineScope {
                repeat(concurrency) {
                    launch {
                        for (key in queue) {
                            // 先占住 running 再减 pending：两个计数器不会同时归零，
                            // drain() 因此不可能在任务在途时误判为"已排空"
                            progressState.update { it.copy(running = it.running + 1) }
                            pendingCount.update { (it - 1).coerceAtLeast(0) }
                            progressState.update { it.copy(pending = pendingCount.value) }
                            try {
                                process(key)
                            } finally {
                                progressState.update { it.copy(running = it.running - 1) }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun stop() {
        queue.close()
        worker?.cancel()
        worker = null
        pendingCount.value = 0
        mergeMutex.withLock { pendingMerges.clear() }
    }

    /** 请求提取（非阻塞）：缓存刚增长、或开启了后台主动下载时按 key 入队。 */
    fun request(key: String) {
        if (queue.trySend(key).isSuccess) {
            pendingCount.update { it + 1 }
            progressState.update { it.copy(pending = pendingCount.value) }
        }
    }

    /** 更新"库里有几首"和"已有几首记录"，用于进度展示。 */
    fun updateLibraryCounts(total: Int, extracted: Int) {
        progressState.update { it.copy(total = total, extracted = extracted) }
    }

    /**
     * 等队列排空后做最后一次合并；返回是否发生过合并。
     *
     * 判据用的是 [pendingCount] 与 `running` 这两个自有计数器，**不能用 `Channel.isEmpty`**：
     * `BufferedChannel` 的 `isEmpty` 反映的是内部状态机而非缓冲区内容，刚 `trySend` 之后
     * 仍会返回 true，会让收尾合并被整个跳过。
     */
    suspend fun drain(): Boolean {
        while (pendingCount.value > 0 || progressState.value.running > 0) {
            delay(DRAIN_POLL_MILLIS)
        }
        return flush()
    }

    private suspend fun process(key: String) {
        val target = targets.targetOf(key) ?: return
        val existing = store.read(key)
        val filled = cache.filledSize(key)
        val node = extractionNodeOf(filled = filled, total = target.totalSize)
        // 只认同一个指纹下的"已试过节点"：远端文件换了要重新走一遍节点
        val lastAttempted = attemptedNodes[key]
            ?.takeIf { it.second == target.fingerprint }
            ?.first
        val decision = if (node == null) {
            ExtractionDecision.SKIP
        } else {
            decideExtraction(
                node = node,
                existing = existing,
                currentFingerprint = target.fingerprint,
                lastAttemptedNode = lastAttempted,
            )
        }
        if (decision != ExtractionDecision.SKIP && node != null) {
            attemptedNodes[key] = node to target.fingerprint
        }
        if (decision == ExtractionDecision.SKIP) {
            logger.i {
                "提取跳过 key=$key 节点=${node ?: "未到"}（filled=$filled/${target.totalSize} " +
                    "已试=${lastAttempted ?: "无"} existing=${existing?.fingerprint}/complete=${existing?.complete}）"
            }
            if (existing != null) {
                // 记录已经有了，但库里那一首可能还没有这些字段：重新扫描、换设备、或者上一次
                // 补丁没落库，都会出现"缓存早就满了，却一直没有时长和封面"。这里直接把已有记录
                // 当成一次补丁发出去，缺什么补什么。
                // 每个键每个提取器实例只补发一次：快照里的那一首不会因为我们补了补丁就自动更新，
                // 没有这个标记就会在每次缓存进度事件上重复入库。
                // 只补发"已经完整"的记录：部分记录刚被这次提取写过一遍补丁，再发一次纯属重复；
                // 而完整记录（时长/封面齐全）才是重新扫描后最容易丢失的那份信息。
                if (existing.complete && target.needsPatch(existing) && republishedKeys.add(key)) {
                    logger.i {
                        "已有提取记录但库里缺字段，补发补丁 key=$key " +
                            "title=${existing.title} duration=${existing.duration} hasCover=${existing.hasCover}"
                    }
                    onRecord(target, existing)
                }
                markExtracted()
            }
            return
        }
        logger.i {
            "提取开始 key=$key 节点=$node 决策=$decision filled=$filled/${target.totalSize} " +
                "existing=${existing?.fingerprint}/complete=${existing?.complete}"
        }

        val complete = decision == ExtractionDecision.FULL
        val extracted = try {
            // 内嵌封面（FLAC 的 PICTURE、MP3 的 APIC）都在文件头的元数据块里，30% 的缓存前缀
            // 就足够读出来——封面不必等整首下完。时长仍只在完整文件上采信：部分文件里它只能靠
            // 比特率估算，放到界面上就是错的。
            readTags(key, includeCover = true, includeDuration = complete)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            // 部分文件读不出标签是常态（还没下完），只有完整文件才当作失败
            if (complete) {
                logger.w(messageString = "提取失败 key=$key：${throwable.message}", throwable = throwable)
                progressState.update { it.copy(failed = it.failed + 1) }
            }
            return
        }

        val now = clock()
        if (extracted == null || extracted.isEmpty) {
            // 没有标签的文件是合法状态：记一条空记录，避免每次都重试，但不覆盖文件名派生的信息
            store.write(key, emptyRecord(target, complete, now), null)
            logger.i { "提取完成 key=$key complete=$complete 但没有可用标签（落一条空记录避免重试）" }
            return
        }

        val record = extracted.toRecord(target.fingerprint, complete, now)
        store.write(key, record, extracted.cover)
        logger.i {
            "提取完成 key=$key complete=$complete title=${record.title} " +
                "duration=${record.duration} cover=${extracted.cover?.size ?: 0}B"
        }
        markExtracted()
        onRecord(target, record)

        val shouldFlush = mergeMutex.withLock {
            pendingMerges[key] = record
            pendingMerges.size >= batchSize
        }
        if (shouldFlush) flush()
    }

    /** 把待合并的记录一次性交给上层合并进完整快照。 */
    private suspend fun flush(): Boolean = mergeMutex.withLock {
        if (pendingMerges.isEmpty()) return@withLock false
        val batch = pendingMerges.toMap()
        pendingMerges.clear()
        onFlush(batch)
        true
    }

    private fun emptyRecord(
        target: WebDavExtractionTarget,
        complete: Boolean,
        now: Long,
    ) = WebDavMetadataRecord(
        fingerprint = target.fingerprint,
        complete = complete,
        extractedAt = now,
    )

    private suspend fun readTags(
        key: String,
        includeCover: Boolean,
        includeDuration: Boolean,
    ): WebDavExtractedMetadata? {
        val path = cache.localPath(key) ?: return null
        return tagReader(path, includeCover, includeDuration)
    }

    /** 库里那一首是否还缺这条记录能提供的信息。 */
    private fun WebDavExtractionTarget.needsPatch(record: WebDavMetadataRecord): Boolean {
        val extra = audio.extra.orEmpty()
        val currentDuration = extra[LAudioExtraKeys.Duration]?.toLongOrNull() ?: 0L
        return (record.duration > 0L && currentDuration <= 0L) ||
            (record.title.isNotBlank() && audio.title != record.title) ||
            (record.album.isNotBlank() && extra[LAudioExtraKeys.AlbumName] != record.album) ||
            (record.artist.isNotBlank() && extra[LAudioExtraKeys.ArtistName] != record.artist)
    }

    private fun markExtracted() {
        progressState.update { it.copy(extracted = it.extracted + 1) }
    }
}

/** 默认的标签读取实现：直接读本地缓存文件（taglib 只接受本地路径 / 文件描述符）。 */
internal suspend fun readTagsFromFile(
    path: String,
    includeCover: Boolean,
    includeDuration: Boolean,
): WebDavExtractedMetadata? {
    val metadata = Taglib.readMetadata(path) ?: return null
    val cover = if (includeCover) {
        runCatching { Taglib.getPicture(path) }.getOrNull()
    } else {
        null
    }
    return WebDavExtractedMetadata(
        title = metadata.title.orEmpty(),
        artist = metadata.artist.orEmpty(),
        album = metadata.album.orEmpty(),
        albumArtist = metadata.albumArtist,
        track = metadata.track,
        disc = metadata.disc,
        date = metadata.date,
        genre = metadata.genre,
        // 只有完整文件才有可信时长
        duration = if (includeDuration) metadata.duration else 0L,
        cover = cover,
    )
}

/** 排空等待的轮询间隔。 */
internal const val DRAIN_POLL_MILLIS = 20L
