package com.lalilu.lmedia.source.webdav

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
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

internal const val PARTIAL_EXTRACTION_THRESHOLD = 0.3

/**
 * 判断这一次缓存增长值不值得读一次标签。
 *
 * - 30% 节点：文件头通常已经包含 ID3v2 / FLAC 元数据块，能提前让标题与歌手可见。
 * - 100% 节点：补齐时长与内嵌封面；**部分提取成功过的记录仍然要再走一次完整提取**。
 * - 指纹变化（远端文件被替换）时缓存记录失效，允许重新提取。
 */
internal fun decideExtraction(
    filled: Long,
    total: Long,
    existingFingerprint: String?,
    existingComplete: Boolean,
    currentFingerprint: String,
): ExtractionDecision {
    val reachedFull = total > 0L && filled >= total
    val reachedPartial = when {
        total > 0L -> filled.toDouble() / total.toDouble() >= PARTIAL_EXTRACTION_THRESHOLD
        // 服务器没给长度：只要有字节就先读一次文件头
        else -> filled > 0L
    }
    if (!reachedFull && !reachedPartial) return ExtractionDecision.SKIP

    val stale = existingFingerprint == null || existingFingerprint != currentFingerprint
    if (stale) return if (reachedFull) ExtractionDecision.FULL else ExtractionDecision.PARTIAL
    if (reachedFull && !existingComplete) return ExtractionDecision.FULL
    return ExtractionDecision.SKIP
}

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
 * 30% / 100% 两个节点的触发来自播放代理，因此**只有听过的歌会被补全**；[request] 也用于
 * "允许后台主动下载"时对未播放歌曲的补全。
 */
@OptIn(ExperimentalTime::class)
internal class WebDavExtractor(
    private val cache: WebDavCache,
    private val store: WebDavMetadataStore,
    private val targets: WebDavExtractionTargets,
    private val progressState: MutableStateFlow<WebDavExtractionState>,
    private val onRecord: suspend (WebDavExtractionTarget, WebDavMetadataRecord) -> Unit,
    private val onFlush: suspend (Map<String, WebDavMetadataRecord>) -> Unit,
    private val concurrency: Int = 2,
    private val batchSize: Int = BATCH_SIZE,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    tagReaderOverride: (suspend (path: String, includeCoverAndDuration: Boolean) -> WebDavExtractedMetadata?)? = null,
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
    private var worker: Job? = null

    /** 标签读取做成可替换的依赖：默认走 taglib，测试可以注入确定性结果。 */
    private val tagReader: suspend (path: String, includeCoverAndDuration: Boolean) -> WebDavExtractedMetadata? =
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
        val decision = decideExtraction(
            filled = cache.filledSize(key),
            total = target.totalSize,
            existingFingerprint = existing?.fingerprint,
            existingComplete = existing?.complete ?: false,
            currentFingerprint = target.fingerprint,
        )
        if (decision == ExtractionDecision.SKIP) {
            if (existing != null) markExtracted()
            return
        }

        val complete = decision == ExtractionDecision.FULL
        val extracted = try {
            readTags(key, includeCoverAndDuration = complete)
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
            return
        }

        val record = extracted.toRecord(target.fingerprint, complete, now)
        store.write(key, record, extracted.cover)
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
        includeCoverAndDuration: Boolean,
    ): WebDavExtractedMetadata? {
        val path = cache.localPath(key) ?: return null
        return tagReader(path, includeCoverAndDuration)
    }

    private fun markExtracted() {
        progressState.update { it.copy(extracted = it.extracted + 1) }
    }
}

/** 默认的标签读取实现：直接读本地缓存文件（taglib 只接受本地路径 / 文件描述符）。 */
internal suspend fun readTagsFromFile(
    path: String,
    includeCoverAndDuration: Boolean,
): WebDavExtractedMetadata? {
    val metadata = Taglib.readMetadata(path) ?: return null
    val cover = if (includeCoverAndDuration) {
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
        duration = if (includeCoverAndDuration) metadata.duration else 0L,
        cover = cover,
    )
}

/** 排空等待的轮询间隔。 */
internal const val DRAIN_POLL_MILLIS = 20L
