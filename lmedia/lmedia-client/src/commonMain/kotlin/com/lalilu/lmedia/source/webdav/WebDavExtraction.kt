package com.lalilu.lmedia.source.webdav

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.stream.StreamCache
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

/**
 * 头部窗口：只要缓存到这么多字节，就先把标签读一遍。
 *
 * 用**固定字节数**而不是"文件大小的百分比"：实测本机 7 首 FLAC 的元数据（含内嵌封面）全部落在
 * 文件开头 1 MB 以内（中位 0.26 MB，最大 0.83 MB），而 30% 阈值对 30 MB 的文件要下 9 MB——
 * 是实际需要的十倍以上，用户要等很久才看到标题与封面。
 */
internal const val HEAD_WINDOW_BYTES = 1L * 1024 * 1024

/**
 * 头部窗口的兜底大小：窗口内什么都没读到时（例如内嵌封面被切断，taglib 解析失败）再扩到这么大试一次。
 *
 * 只扩一次：`WebDavMetadataRecord.examinedBytes` 记着上次尝试时的缓存长度，避免每次缓存增长都重试。
 */
internal const val HEAD_WINDOW_FALLBACK_BYTES = 4L * 1024 * 1024

/**
 * 判断这一次缓存增长值不值得读一次标签。
 *
 * - 头部窗口（[HEAD_WINDOW_BYTES]）：FLAC/MP3 的标签与内嵌封面都在文件开头，通常几百 KB 就够，
 *   让标题/封面**远早于整首下载完成**就能显示。
 * - 100% 节点：补齐时长与完整封面；部分提取成功过的记录仍然要再走一次完整提取。
 * - 窗口内读不出东西时，扩到 [HEAD_WINDOW_FALLBACK_BYTES] 再试一次。
 * - 指纹变化（远端文件被替换）时缓存记录失效，允许重新提取。
 */
internal fun decideExtraction(
    filled: Long,
    total: Long,
    existingFingerprint: String?,
    existingComplete: Boolean,
    currentFingerprint: String,
    /** 上一次尝试提取时缓存里有多少字节；0 表示没有可用记录。 */
    existingExaminedBytes: Long = 0L,
): ExtractionDecision {
    val reachedFull = total > 0L && filled >= total
    val headWindow = if (total > 0L) minOf(total, HEAD_WINDOW_BYTES) else HEAD_WINDOW_BYTES
    val fallbackWindow = if (total > 0L) {
        minOf(total, HEAD_WINDOW_FALLBACK_BYTES)
    } else {
        HEAD_WINDOW_FALLBACK_BYTES
    }
    // 服务器没给长度时无法按窗口判断：只要有字节就先读一次文件头
    val reachedHead = if (total > 0L) filled >= headWindow else filled > 0L
    if (!reachedFull && !reachedHead) return ExtractionDecision.SKIP

    val stale = existingFingerprint == null || existingFingerprint != currentFingerprint
    if (stale) return if (reachedFull) ExtractionDecision.FULL else ExtractionDecision.PARTIAL
    if (reachedFull && !existingComplete) return ExtractionDecision.FULL

    // 头部窗口试过但没读出东西：给一次更大的窗口
    val triedSmallerWindow = existingExaminedBytes in 1 until fallbackWindow
    if (!existingComplete && triedSmallerWindow && filled >= fallbackWindow) {
        return ExtractionDecision.PARTIAL
    }
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
 * 头部窗口 / 100% 两个节点的触发来自播放代理，因此**只有听过的歌会被补全**；[request] 也用于
 * "允许后台主动下载"时对未播放歌曲的补全。
 */
@OptIn(ExperimentalTime::class)
internal class WebDavExtractor(
    private val cache: StreamCache,
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
        val filled = cache.prefixSize(key)
        val decision = decideExtraction(
            filled = filled,
            total = target.totalSize,
            existingFingerprint = existing?.fingerprint,
            existingComplete = existing?.complete ?: false,
            currentFingerprint = target.fingerprint,
            existingExaminedBytes = existing?.examinedBytes ?: 0L,
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
            // 没有标签的文件是合法状态：记一条空记录，避免每次都重试，但不覆盖文件名派生的信息。
            // examinedBytes 让"头部窗口试过且没结果"这件事可判：兜底窗口只扩大一次。
            store.write(key, emptyRecord(target, complete, now, filled), null)
            return
        }

        val record = extracted.toRecord(target.fingerprint, complete, now, examinedBytes = filled)
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
        examinedBytes: Long,
    ) = WebDavMetadataRecord(
        fingerprint = target.fingerprint,
        complete = complete,
        extractedAt = now,
        examinedBytes = examinedBytes,
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
