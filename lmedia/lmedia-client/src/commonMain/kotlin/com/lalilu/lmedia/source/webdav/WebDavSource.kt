package com.lalilu.lmedia.source.webdav

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.common.kv.KVItem
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaSourcePatchSource
import com.lalilu.lmedia.domain.source.MediaSourceStateStore
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

/**
 * 基于 WebDAV 的媒体源。
 *
 * 首扫只做目录遍历（PROPFIND），用文件名与目录结构派生可读元数据，快照立即可用；真实 tag 与封面
 * 在播放时顺路提取（后续阶段接入）。播放链路统一走本机回环代理，因此这里不向播放器暴露凭据。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class WebDavSource(
    private val clientFactory: WebDavClientFactory,
    private val cacheRootProvider: WebDavCacheRootProvider,
    kv: LMediaKV,
) : MediaSource, MediaDataSource, MediaSourcePatchSource, CoroutineScope {

    companion object {
        private const val TAG = "WebDavSource"

        /** 目录遍历的并发上限：避免瞬时打满家用 NAS 的连接数。 */
        internal const val MAX_DIR_CONCURRENCY = 4

        /** 目录数量上限，防止畸形挂载点导致无限展开。 */
        internal const val MAX_DIRECTORIES = 20_000

        internal const val EXTRA_PATH = "path"
        internal const val EXTRA_DIR_PATH = "dir_path"
        internal const val EXTRA_ETAG = "etag"
        internal const val EXTRA_FILE_SIZE = "file_size"
        internal const val EXTRA_LAST_MODIFIED = "last_modified"
        internal const val EXTRA_CONTENT_TYPE = "content_type"
    }

    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Logger.e(tag = TAG, throwable = throwable, messageString = "${throwable.message}")
        }

    override val name: String = TAG
    override val dataSource: MediaDataSource = this
    private val logger = Logger.withTag(name)

    private val stateStore = MediaSourceStateStore()
    override val state: StateFlow<SnapshotState> = stateStore.state
    override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    override val contentState = stateStore.contentState

    /** 逐条补全的元数据走这条通道入库；完整快照的整源对账代价与库大小相关，不适合单曲更新。 */
    private val patchFlow = MutableSharedFlow<LAudio>(extraBufferCapacity = 64)
    override val audioPatches: Flow<LAudio> = patchFlow.asSharedFlow()

    private var client: WebDavClient? = null
    private var loadingJob: Job? = null

    /** 播放代理与它需要的目标表；目标表按需登记，只保存当前会话已请求过的歌曲。 */
    private var proxy: WebDavProxy? = null
    private val proxyMutex = Mutex()
    private val targetMutex = Mutex()
    private val streamTargets = mutableMapOf<String, WebDavStreamTarget>()

    val config: KVItem<WebDavConfig> = kv.obtain(
        key = "${name}Config",
        defaultValue = WebDavConfig.Empty,
    ).apply { disableAutoSave() }
    private var activeConfig: WebDavConfig = config.value

    override fun init() {
        if (cacheRootProvider.cacheRoot() == null) {
            // Web 没有本地文件系统：既做不了读穿缓存，也起不了回环代理
            stateStore.content.unavailable("当前平台不支持 WebDAV 数据源")
            return
        }
        if (activeConfig.isConfigured) {
            scan(isInitialize = true, preserveReady = false)
        } else {
            stateStore.content.unavailable("Server not configured")
        }
    }

    override suspend fun deactivate() {
        loadingJob?.cancelAndJoin()
        loadingJob = null
        proxy?.stop()
        proxy = null
        targetMutex.withLock { streamTargets.clear() }
        client?.close()
        client = null
        stateStore.reset()
        stateStore.content.unavailable("Source disabled")
    }

    /**
     * 保存连接配置并发起扫描。密码留空时，仅在地址与用户名都没变的情况下复用既有密码；
     * 用户名与密码都留空按匿名访问处理。
     */
    fun connect(url: String, username: String, password: String, rootPath: String): Result<Unit> =
        runCatching {
            require(cacheRootProvider.cacheRoot() != null) { "当前平台不支持 WebDAV 数据源" }
            val current = activeConfig
            val normalizedUrl = normalizeServerUrl(url)
            val normalizedRoot = normalizeDirectoryPath(rootPath)
            val trimmedUsername = username.trim()
            val anonymous = trimmedUsername.isBlank() && password.isBlank()
            val canReusePassword = password.isBlank() && !anonymous &&
                normalizedUrl == current.url &&
                trimmedUsername == current.username &&
                current.password.isNotBlank()
            require(anonymous || password.isNotBlank() || canReusePassword) { "请输入密码" }

            config.value = current.copy(
                url = normalizedUrl,
                rootPath = normalizedRoot,
                username = trimmedUsername,
                password = if (canReusePassword) current.password else password,
            )
            config.save()
            activeConfig = config.value
            scan(preserveReady = false)
        }

    fun retry(): Result<Unit> = runCatching {
        require(activeConfig.isConfigured) { "请先填写服务器地址" }
        scan()
    }

    fun refresh(): Result<Unit> = retry()

    fun cancel() {
        loadingJob?.cancel()
        stateStore.content.unavailable("Cancelled", preserveReady = true)
    }

    /** 只清除已保存的凭据，保留地址与根目录，便于用户重新输入密码。 */
    fun reset() {
        loadingJob?.cancel()
        client?.close()
        client = null
        stateStore.content.unavailable("Authentication cleared")
        loadingJob = launch { stateStore.reset() }
        config.value = activeConfig.copy(username = "", password = "")
        config.save()
        activeConfig = config.value
    }

    private fun scan(
        isInitialize: Boolean = false,
        preserveReady: Boolean = true,
    ) {
        val taskId = stateStore.tryBegin(
            if (isInitialize) "Restoring connection..." else "Connecting..."
        ) ?: return

        loadingJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                yield()
                if (!stateStore.isActive(taskId)) return@launch
                stateStore.content.preparing(preserveReady = preserveReady)

                client?.close()
                val activeClient = clientFactory.create(activeConfig).also { client = it }
                ensureProxy()
                val audios = scanLibrary(activeClient, taskId)

                if (stateStore.succeed(taskId, audios) != null) {
                    stateStore.content.ready()
                }
            } catch (cancelled: CancellationException) {
                if (stateStore.cancel(taskId)) {
                    stateStore.content.unavailable("Cancelled", preserveReady = true)
                }
                throw cancelled
            } catch (throwable: Throwable) {
                logger.e(messageString = "扫描失败: ${throwable.message}", throwable = throwable)
                if (isInitialize) {
                    if (stateStore.fail(taskId, throwable.message ?: "Connection failed")) {
                        stateStore.reset()
                        stateStore.content.unavailable(throwable.message ?: "Connection failed")
                    }
                } else {
                    if (stateStore.fail(taskId, "[$name]${throwable.message}")) {
                        stateStore.content.unavailable(
                            throwable.message ?: "Connection failed",
                            preserveReady = preserveReady,
                        )
                    }
                }
            }
        }
    }

    /**
     * 逐层遍历目录树。
     *
     * 单个子目录请求失败只跳过该目录，不拖垮整次扫描——家用 NAS 上偶发的超时不应让整个媒体库不可用。
     * 但**根目录失败是致命的**：那意味着地址、鉴权或根路径有误，此时若按"空结果"提交成功快照，
     * 数据库会把该源已有的歌曲全部标记为不可用，一次输错密码就会让用户的媒体库消失。
     */
    private suspend fun scanLibrary(activeClient: WebDavClient, taskId: Long): List<LAudio> {
        val root = normalizeDirectoryPath(activeConfig.rootPath)
        val audios = mutableListOf<LAudio>()
        val visited = mutableSetOf(root)
        val semaphore = Semaphore(MAX_DIR_CONCURRENCY)
        var pending = listOf(root)
        var processed = 0
        var successfulRequests = 0
        var firstFailure: Throwable? = null

        while (pending.isNotEmpty()) {
            val batch = pending
            val results = coroutineScope {
                batch.map { directory ->
                    async {
                        semaphore.withPermit {
                            runCatchingCancellable { activeClient.propfind(directory, 1) }
                        }
                    }
                }.awaitAll()
            }

            processed += batch.size
            val next = mutableListOf<String>()
            results.forEachIndexed { index, result ->
                val directory = batch[index]
                result.onSuccess { entries ->
                    successfulRequests += 1
                    entries.forEach { entry ->
                        when {
                            entry.isDirectory -> {
                                val child = entry.path.let { if (it.endsWith('/')) it else "$it/" }
                                if (visited.add(child)) next += child
                            }

                            WebDavNamingRules.isAudioFile(entry.path) -> audios += entry.toAudio()
                        }
                    }
                }.onFailure { throwable ->
                    if (directory == root) throw throwable
                    if (firstFailure == null) firstFailure = throwable
                    logger.w(
                        messageString = "跳过目录 $directory：${throwable.message}",
                        throwable = throwable,
                    )
                }
            }

            check(visited.size <= MAX_DIRECTORIES) {
                "目录数量超过上限（$MAX_DIRECTORIES），请确认库根目录是否指向了过大的挂载点"
            }

            pending = next
            stateStore.updateLoading(
                taskId = taskId,
                message = "扫描目录 $processed 个，发现 ${audios.size} 首",
                progress = progressOf(processed, next.size),
            )
        }

        if (successfulRequests == 0) {
            throw firstFailure ?: IllegalStateException("未能读取任何目录")
        }

        return audios.sortedWith(compareBy({ it.artistName }, { it.albumName ?: "" }, { it.title }))
    }

    /** 剩余队列越少进度越高；结束前的进度不会顶到 100%，避免与"已就绪"混淆。 */
    private fun progressOf(processed: Int, remaining: Int): Float {
        val total = processed + remaining
        if (total <= 0) return 0.9f
        return (processed.toFloat() / total.toFloat() * 0.9f).coerceIn(0f, 0.9f)
    }

    private fun WebDavEntry.toAudio(): LAudio {
        val naming = WebDavNamingRules.derive(path, rootPath = activeConfig.rootPath)
        val directoryPath = path.substringBeforeLast('/', "")
        return LAudio(
            id = "${LAudio.ID_PREFIX}${"${activeConfig.url}|$path".md5()}",
            title = naming.title,
            subtitle = naming.artist.orEmpty(),
            mediaSourceName = name,
            extra = buildMap {
                put(EXTRA_PATH, path)
                put(EXTRA_DIR_PATH, directoryPath)
                etag?.let { put(EXTRA_ETAG, it) }
                if (contentLength > 0L) put(EXTRA_FILE_SIZE, contentLength.toString())
                lastModified?.let { put(EXTRA_LAST_MODIFIED, it) }
                contentType?.let { put(EXTRA_CONTENT_TYPE, it) }
                naming.artist?.takeIf(String::isNotBlank)
                    ?.let { put(LAudioExtraKeys.ArtistName, it) }
                naming.album?.takeIf(String::isNotBlank)
                    ?.let { put(LAudioExtraKeys.AlbumName, it) }
                naming.track?.takeIf(String::isNotBlank)
                    ?.let { put(LAudioExtraKeys.Track, it) }
            },
        )
    }

    /** 需要在取消时立即中止，不能让 runCatching 把 CancellationException 当成普通失败。 */
    private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    // 播放统一走本机回环代理：播放器拿到的地址里没有凭据，字节顺便沉淀成本地缓存。
    override suspend fun getMedia(song: LAudio): MediaData? {
        val path = song.extra?.get(EXTRA_PATH)?.takeIf(String::isNotBlank) ?: return null
        val activeProxy = proxy ?: return null
        val key = cacheKeyOf(song.id)
        val url = activeProxy.audioUrl(key) ?: return null

        // 先登记再返回地址：代理收到请求时的 resolve 必须能查到它
        targetMutex.withLock {
            streamTargets[key] = WebDavStreamTarget(
                path = path,
                totalSize = song.extra?.get(EXTRA_FILE_SIZE)?.toLongOrNull() ?: 0L,
                contentType = song.extra?.get(EXTRA_CONTENT_TYPE),
            )
        }
        return MediaData.Url(url)
    }

    /** 启动回环代理；失败时抛出，让本次扫描以明确原因失败，而不是静默变成"不可播"。 */    private suspend fun ensureProxy(): WebDavProxy {
        proxy?.let { return it }
        return proxyMutex.withLock {
            proxy?.let { return@withLock it }
            val cacheRoot = cacheRootProvider.cacheRoot()
                ?: throw WebDavException.Unexpected("当前平台不支持 WebDAV 数据源")
            val created = WebDavProxy(
                cache = WebDavCache(cacheRoot),
                backend = ProxyBackend(),
            )
            created.start()
            proxy = created
            created
        }
    }

    /**
     * 把补全后的单曲结果逐条发布出去（由提取流程调用）。
     *
     * 只负责送到增量通道；把它合并回完整快照由提取流程按批处理，否则下一次全量对账会把补丁覆盖回去。
     */
    internal suspend fun emitAudioPatch(audio: LAudio) {
        patchFlow.emit(audio)
    }

    private fun cacheKeyOf(audioId: String): String = audioId.md5()

    /** 传给代理的回调实现：只在数据源内部使用，因此不暴露成公开接口实现。 */
    private inner class ProxyBackend : WebDavProxyBackend {
        override suspend fun resolve(key: String): WebDavStreamTarget? =
            targetMutex.withLock { streamTargets[key] }

        override suspend fun fetch(
            target: WebDavStreamTarget,
            start: Long,
            endInclusive: Long?,
            onChunk: suspend (ByteArray) -> Unit,
        ) {
            val activeClient = client ?: throw WebDavException.Unexpected("数据源已断开")
            activeClient.fetchRange(target.path, start, endInclusive, onChunk)
        }
    }
}
