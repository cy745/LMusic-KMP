package com.lalilu.lmedia.cache

import com.lalilu.common.ext.md5
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * 通用在线缓存：一个回环代理 + 一份读穿缓存，所有在线数据源共用。
 *
 * 数据源只需要回答三个问题——缓存键是什么、访问原始地址要带什么头、文件多大——就能拿到
 * "边播边缓存、听过的部分自动离线可用"。除此之外它不必知道代理、缓存目录或 Range 的存在。
 *
 * 缓存键由数据源自己决定（默认是原始地址），这里只做一次"能不能直接当文件名"的归一化。
 */
@Single
class MediaCacheController(
    private val cacheRootProvider: MediaCacheRootProvider,
    private val json: Json,
) {
    companion object {
        /** 通用缓存目录名：与具体数据源解耦，所有在线源共享同一份配额。 */
        internal const val NAMESPACE = "media"

        /** 缓存进度轮询间隔：够跟上播放缓冲条，又不至于每帧都读磁盘。 */
        private const val PROGRESS_POLL_INTERVAL_MS = 500L
    }

    private var upstream = HttpRangeUpstream()
    private var upstreamClosed = false
    private val mutex = Mutex()
    private val listenerMutex = Mutex()
    private var proxy: LoopbackRangeProxy? = null
    private var store: MediaCacheStore? = null
    private val progressListeners = mutableListOf<suspend (String) -> Unit>()

    /** 缓存配额（字节，0 表示不限）。由数据源按自己的设置写入，改动立即生效。 */
    var quotaBytes: () -> Long = { 0L }

    /** 仅供同模块的提取器读取本地文件；未启动时为空。 */
    internal val storeOrNull: MediaCacheStore? get() = store

    /**
     * 键会直接当文件名用：含 `/`、`:`、`?` 的地址必须压成摘要，否则会写到缓存目录之外。
     * 数据源已给出摘要形式的键时原样保留，保证缓存与提取用的键完全一致。
     */
    private fun fileNameOf(key: String): String =
        if (key.isNotEmpty() && key.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
            key
        } else {
            key.md5()
        }

    /**
     * 监听"缓存变长了"：提取元数据、刷新进度都挂在这里。
     *
     * 同一个监听器只注册一次；[removeProgressListener] 用同一个引用移除，数据源停用时必须摘掉，
     * 否则会对着已经销毁的提取器继续发事件。
     */
    suspend fun addProgressListener(listener: suspend (String) -> Unit) {
        listenerMutex.withLock {
            if (progressListeners.none { it === listener }) progressListeners += listener
        }
    }

    suspend fun removeProgressListener(listener: suspend (String) -> Unit) {
        listenerMutex.withLock { progressListeners.removeAll { it === listener } }
    }

    /**
     * 注册上游并返回可播放的本机地址；平台没有文件系统时返回 null（调用方应回退到原始地址）。
     */
    suspend fun register(key: String, origin: CachedOrigin): String? {
        val active = ensureStarted() ?: return null
        val fileKey = fileNameOf(key)
        upstream.register(fileKey, origin)
        // 这是"正在播放的那一首"：即便暂停中、没有在途请求，也不允许被淘汰。
        active.pinPlayback(fileKey)
        return active.audioUrl(fileKey)
    }

    /** 停掉所有没有播放请求附着的后台预取下载（关开关 / 网络门控时调用）。 */
    suspend fun cancelBackgroundDownloads() {
        proxy?.cancelBackgroundDownloads()
    }

    /** 已缓存的前缀长度；没有缓存时返回 0。 */
    suspend fun filledSize(key: String): Long = ensureStarted()?.let { store?.filledSize(fileNameOf(key)) } ?: 0L

    /** 本地文件路径：整首缓存完成后提取标签用。 */
    suspend fun localPath(key: String): String? = ensureStarted()?.let { store?.localPath(fileNameOf(key)) }

    /** 主动补齐缓存（后台下载）；返回补齐后的长度。 */
    suspend fun ensureCached(
        key: String,
        origin: CachedOrigin,
        upTo: Long = Long.MAX_VALUE,
    ): Long {
        val active = ensureStarted() ?: return 0L
        val fileKey = fileNameOf(key)
        upstream.register(fileKey, origin)
        return active.ensureCached(
            key = fileKey,
            target = UpstreamTarget(
                token = fileKey,
                totalSize = origin.totalSize,
                contentType = origin.contentType,
            ),
            upTo = upTo,
        )
    }

    /**
     * 缓存进度（0..1）。
     *
     * 对读穿缓存来说"缓冲到哪里"就是"本地已经缓存了多少"——包含之前播放与后台补全沉淀下来的
     * 部分，那正是用户可以不等待下载就直接跳过去的位置。
     */
    fun progress(key: String, totalSize: Long): Flow<Float> = flow {
        ensureStarted()
        val fileKey = fileNameOf(key)
        while (true) {
            val filled = store?.filledSize(fileKey) ?: 0L
            emit(
                if (totalSize > 0L) {
                    (filled.toFloat() / totalSize).coerceIn(0f, 1f)
                } else {
                    0f
                }
            )
            delay(PROGRESS_POLL_INTERVAL_MS)
        }
    }

    internal suspend fun store(): MediaCacheStore? = ensureStarted()?.let { store }

    /** 停掉代理并清空注册表；应用退出或测试收尾时调用。 */
    suspend fun stop() {
        mutex.withLock {
            proxy?.stop()
            proxy = null
            store = null
            listenerMutex.withLock { progressListeners.clear() }
            upstream.clear()
            // 连接池也要释放：只停回环服务会留下 HttpClient 的线程/连接
            upstream.close()
            upstreamClosed = true
        }
    }

    private suspend fun ensureStarted(): LoopbackRangeProxy? {
        proxy?.let { return it }
        return mutex.withLock {
            proxy?.let { return@withLock it }
            val root = cacheRootProvider.cacheRoot() ?: return@withLock null
            // stop() 之后再用就必须换一个新的客户端：close 过的 HttpClient 不能再发请求
            if (upstreamClosed) {
                upstream = HttpRangeUpstream()
                upstreamClosed = false
            }
            val cache = MediaCacheStore(root, json, namespace = NAMESPACE).also { store = it }
            val created = LoopbackRangeProxy(
                cache = cache,
                upstream = upstream,
                // 先取快照再回调：监听器里可能反过来注册/移除自己，不能在持锁时调用
                onCacheProgress = { key ->
                    val snapshot = listenerMutex.withLock { progressListeners.toList() }
                    snapshot.forEach { it(key) }
                },
                // 每次需要时读配额：改设置立即生效，不用重建代理
                quotaBytes = { quotaBytes() },
            )
            created.start()
            proxy = created
            created
        }
    }
}

/**
 * 从地址扩展名推断媒体类型。
 *
 * 只是给播放器一个提示，猜不到（或猜错）时返回 null —— 代理会回 `application/octet-stream`，
 * 播放器仍然能靠字节嗅探选对解封装器。
 */
internal fun contentTypeOf(url: String): String? =
    url.substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('.', "")
        .lowercase()
        .let { extension ->
            when (extension) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                "ogg", "oga" -> "audio/ogg"
                "opus" -> "audio/opus"
                "ape" -> "audio/x-ape"
                "wv" -> "audio/x-wavpack"
                "dsf" -> "audio/x-dsf"
                else -> null
            }
        }
