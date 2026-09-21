package com.lalilu.lmedia.source.webdav

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.common.kv.KVItem
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaFetchOptions
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaSourceBufferProgress
import com.lalilu.lmedia.domain.source.MediaSourcePatchSource
import com.lalilu.lmedia.domain.source.MediaSourceStateStore
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.net.NetworkObservation
import com.lalilu.lmedia.net.NetworkType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

/**
 * 基于 WebDAV 的媒体源。
 *
 * 首扫只做目录遍历（PROPFIND），用文件名与目录结构派生可读元数据，快照立即可用。
 * 播放统一走本机回环代理：字节顺便沉淀成本地缓存，并在缓存达到头部窗口（默认 1 MB）与整首时读取真实标签，
 * 逐条写入数据库（[audioPatches]）并按批合并回完整快照。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class WebDavSource(
    private val clientFactory: WebDavClientFactory,
    private val cacheRootProvider: WebDavCacheRootProvider,
    private val json: Json,
    kv: LMediaKV,
    private val networkObservation: NetworkObservation,
) : MediaSource, MediaDataSource, MediaSourcePatchSource, MediaSourceBufferProgress, CoroutineScope {

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

        /** 同目录边车文件：封面图与同名 `.lrc`，零额外请求就能拿到路径。 */
        internal const val EXTRA_COVER_PATH = "cover_path"
        internal const val EXTRA_COVER_FINGERPRINT = "cover_fingerprint"
        internal const val EXTRA_LYRIC_PATH = "lyric_path"
        internal const val EXTRA_LYRIC_FINGERPRINT = "lyric_fingerprint"
        internal const val DEFAULT_META_REMOTE_PATH = "/.lmusic/"

        internal fun cacheKeyOf(audioId: String): String = audioId.md5()
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

    private val mutableProgress = MutableStateFlow(WebDavExtractionState())

    private val mutableBufferSnapshot = MutableStateFlow<WebDavBufferSnapshot?>(null)

    /** 提取进度，供源卡片展示。 */
    val extractionProgress: StateFlow<WebDavExtractionState> = mutableProgress

    private val mutableMetadataSync = MutableStateFlow<WebDavMetadataSyncState>(WebDavMetadataSyncState.Idle)

    /** 元数据远端同步状态；只读目录、网络失败都会在这里给出可展示的原因。 */
    val metadataSyncState: StateFlow<WebDavMetadataSyncState> = mutableMetadataSync

    private val mutableNetworkType = MutableStateFlow(NetworkType.UNKNOWN)

    /** 当前网络类型；卡片据此说明"为什么后台任务停了"。 */
    val networkType: StateFlow<NetworkType> = mutableNetworkType

    private val mutableBackgroundPaused = MutableStateFlow(false)

    /** 后台传输是否已被网络门控暂停（移动网络/未知网络且用户没有手动放行）。 */
    val backgroundPaused: StateFlow<Boolean> = mutableBackgroundPaused

    /** 用户手动放行：只对当前网络类型有效，网络一变就重新按规则判断。 */
    private val mutableMeteredAllowed = MutableStateFlow(false)

    /** 仅供测试与诊断：提取结果的本地存储。 */
    internal val metadataStoreOrNull: WebDavMetadataStore? get() = metadataStore

    private var client: WebDavClient? = null
    private var loadingJob: Job? = null
    private var backgroundJob: Job? = null
    private var syncJob: Job? = null
    private var networkJob: Job? = null

    private var proxy: WebDavProxy? = null
    private var cache: WebDavCache? = null
    private var metadataStore: WebDavMetadataStore? = null
    private var metadataRemote: WebDavMetadataRemote? = null
    private var extractor: WebDavExtractor? = null
    private val setupMutex = Mutex()

    /** 缓存键 → 歌曲：代理与提取都需要按 key 找回远端路径与指纹。 */
    private val indexMutex = Mutex()
    private val keyToAudio = mutableMapOf<String, LAudio>()

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
        observeNetwork()
        if (activeConfig.isConfigured) {
            scan(isInitialize = true, preserveReady = false)
        } else {
            stateStore.content.unavailable("Server not configured")
        }
    }

    override suspend fun deactivate() {
        loadingJob?.cancelAndJoin()
        loadingJob = null
        backgroundJob?.cancelAndJoin()
        backgroundJob = null
        syncJob?.cancelAndJoin()
        syncJob = null
        networkJob?.cancelAndJoin()
        networkJob = null
        mutableMeteredAllowed.value = false
        mutableBackgroundPaused.value = false
        extractor?.stop()
        extractor = null
        proxy?.stop()
        proxy = null
        metadataStore = null
        metadataRemote = null
        cache = null
        indexMutex.withLock { keyToAudio.clear() }
        mutableProgress.value = WebDavExtractionState()
        mutableMetadataSync.value = WebDavMetadataSyncState.Idle
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

    /**
     * 用户要求在当前网络（通常是移动网络）上继续后台任务。
     *
     * 只对当前网络类型有效：网络一变就重新按规则判断，避免"一次放行"变成永久关闭门控。
     */
    fun allowMeteredTransfer() {
        mutableMeteredAllowed.value = true
        applyNetworkGate(resume = true)
    }

    /** 后台传输是否被网络门控拦住。播放请求不受影响——那是用户此刻主动要的流量。 */
    private fun blockedByNetwork(): Boolean =
        !mutableMeteredAllowed.value && !mutableNetworkType.value.allowsBackgroundTransfer

    /** 订阅网络类型；重复调用无副作用。 */
    private fun observeNetwork() {
        if (networkJob != null) return
        networkJob = launch {
            networkObservation.observe()
                .catch { emit(NetworkType.UNKNOWN) }
                .collect { type ->
                    if (mutableNetworkType.value == type) return@collect
                    mutableNetworkType.value = type
                    mutableMeteredAllowed.value = false
                    logger.i(messageString = "网络类型变为 $type")
                    applyNetworkGate(resume = true)
                }
        }
    }

    /**
     * 把门控结果落到后台任务上：被拦住就停掉正在跑的，恢复了就从当前快照继续。
     *
     * 暂停不丢东西：已下载的缓存前缀、已提取的元数据都保留在本地，恢复后从断点继续。
     */
    private fun applyNetworkGate(resume: Boolean) {
        val blocked = blockedByNetwork()
        mutableBackgroundPaused.value = blocked
        if (blocked) {
            backgroundJob?.cancel()
            backgroundJob = null
            syncJob?.cancel()
            syncJob = null
            if (mutableMetadataSync.value == WebDavMetadataSyncState.Syncing) {
                mutableMetadataSync.value = WebDavMetadataSyncState.Idle
            }
            return
        }
        if (!resume) return
        snapshot.value?.let { startBackgroundFetch(it.audios) }
        if (activeConfig.metaSyncEnabled) startMetadataSync()
    }

    /**
     * 更新开关类配置，立即生效。
     *
     * 两个开关语义不同：[backgroundFetchEnabled] 是"主动把没听过的歌下载下来补全"，
     * [metaSyncEnabled] 是"把提取结果与 WebDAV 目录双向同步"。后者打开时会立刻做一次
     * 增量同步，只传远端缺的那几条，并在只读目录上优雅失败。
     *
     * 两者都受网络门控约束：非 Wi-Fi/以太网下不启动，卡片上可手动继续。
     */
    fun updateOptions(
        backgroundFetchEnabled: Boolean = activeConfig.backgroundFetchEnabled,
        metaSyncEnabled: Boolean = activeConfig.metaSyncEnabled,
    ): Result<Unit> = runCatching {
        config.value = activeConfig.copy(
            backgroundFetchEnabled = backgroundFetchEnabled,
            metaSyncEnabled = metaSyncEnabled,
        )
        config.save()
        activeConfig = config.value

        if (backgroundFetchEnabled) {
            snapshot.value?.let { startBackgroundFetch(it.audios) } ?: Unit
        } else {
            backgroundJob?.cancel()
            backgroundJob = null
        }

        if (metaSyncEnabled) {
            ensureMetadataRemote()
            startMetadataSync()
        } else {
            syncJob?.cancel()
            syncJob = null
            metadataRemote = null
            mutableMetadataSync.value = WebDavMetadataSyncState.Idle
        }
    }

    /**
     * 元数据双向同步：本地缺的从远端拉回，远端缺的补传上去。
     *
     * 只做增量：先列一次远端目录拿到已有键，再补齐差集。因此日常开销是"一次 PROPFIND +
     * 少量 PUT/GET"，而不是每首歌都探一遍远端。
     *
     * **已经在同步时直接返回**：扫描结束与开关切换都可能触发同步，两者撞在一起会让后启动的
     * 取消先启动的、同一份工作做两遍，最终状态还只反映后一次（看起来像"什么都没同步"）。
     * 由于新记录在提取时就会各自上传，少启动一次不会漏掉任何一个键。
     */
    private fun startMetadataSync() {
        if (syncJob?.isActive == true) return
        if (blockedByNetwork()) {
            mutableBackgroundPaused.value = true
            return
        }
        val remote = metadataRemote ?: return
        val store = metadataStore ?: return
        syncJob = launch {
            mutableMetadataSync.value = WebDavMetadataSyncState.Syncing
            try {
                // 只读挂载在这里就会失败：目录建不出来 → 直接给出可展示的原因
                remote.ensureReady()
                val remoteKeys = remote.keys()
                val localKeys = store.localKeys()

                var uploaded = 0
                (localKeys - remoteKeys).forEach { key ->
                    if (store.upload(key)) uploaded += 1
                }
                val pulled = store.pullFromRemote(remoteKeys - localKeys)

                mutableMetadataSync.value = WebDavMetadataSyncState.Synced(
                    uploaded = uploaded,
                    pulled = pulled,
                )
                if (uploaded > 0 || pulled > 0) {
                    logger.i(messageString = "元数据同步完成：上传 $uploaded，拉回 $pulled")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                logger.w(messageString = "元数据同步失败：${throwable.message}", throwable = throwable)
                mutableMetadataSync.value = WebDavMetadataSyncState.Failed(
                    message = throwable.message ?: "元数据同步失败",
                    readOnly = throwable is WebDavException.Forbidden,
                )
            }
        }
    }

    /**
     * 第一次遇到远端写不进去（只读目录等）时提示一次，不刷屏。
     *
     * **同步进行中不上报**：同步任务会在结束时给出自己的结论（成功条数或失败原因），
     * 此时抢先把状态改成 Failed 会让用户看到"失败"，而任务其实把该做的都做完了。
     */
    private fun reportRemoteFailure(throwable: Throwable) {
        if (syncJob?.isActive == true) return
        if (mutableMetadataSync.value is WebDavMetadataSyncState.Failed) return
        mutableMetadataSync.value = WebDavMetadataSyncState.Failed(
            message = throwable.message ?: "元数据同步失败",
            readOnly = throwable is WebDavException.Forbidden,
        )
    }

    private fun ensureMetadataRemote(): WebDavMetadataRemote? {
        metadataRemote?.let { return it }
        val activeClient = client ?: return null
        return HttpWebDavMetadataRemote(
            client = activeClient,
            rootPath = activeConfig.metaRemotePath,
            rootFallback = DEFAULT_META_REMOTE_PATH,
        ).also { metadataRemote = it }
    }

    private fun scan(
        isInitialize: Boolean = false,
        preserveReady: Boolean = true,
    ) {
        val taskId = stateStore.tryBegin(
            if (isInitialize) "Restoring connection..." else "Connecting..."
        ) ?: return

        observeNetwork()
        loadingJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                yield()
                if (!stateStore.isActive(taskId)) return@launch
                stateStore.content.preparing(preserveReady = preserveReady)

                client?.close()
                val activeClient = clientFactory.create(activeConfig).also { client = it }
                // 客户端换了，绑在旧客户端上的远端层必须重建
                metadataRemote = null
                if (activeConfig.metaSyncEnabled) ensureMetadataRemote()
                val activeProxy = ensureProxy()
                val extractor = ensureExtractor()

                val audios = scanLibrary(activeClient, taskId)
                val published = stateStore.succeed(taskId, audios)

                if (published != null) {
                    stateStore.content.ready()
                    indexMutex.withLock {
                        keyToAudio.clear()
                        audios.forEach { keyToAudio[cacheKeyOf(it.id)] = it }
                    }
                    refreshLibraryCounts(audios)
                    extractor.start(this)
                    startBackgroundFetch(audios)
                    if (activeConfig.metaSyncEnabled) startMetadataSync()
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
                    // 边车文件（封面图 / 同名 .lrc）就混在这一层的条目里，挑一次给本目录所有音频用
                    val cover = WebDavNamingRules.pickCover(entries)
                    entries.forEach { entry ->
                        when {
                            entry.isDirectory -> {
                                val child = entry.path.let { if (it.endsWith('/')) it else "$it/" }
                                if (visited.add(child)) next += child
                            }

                            WebDavNamingRules.isAudioFile(entry.path) -> audios += entry.toAudio(
                                cover = cover,
                                lyric = WebDavNamingRules.pickLyric(entries, entry),
                            )
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

    private fun WebDavEntry.toAudio(cover: WebDavEntry?, lyric: WebDavEntry?): LAudio {
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
                cover?.let {
                    put(EXTRA_COVER_PATH, it.path)
                    put(EXTRA_COVER_FINGERPRINT, it.fingerprint)
                }
                lyric?.let {
                    put(EXTRA_LYRIC_PATH, it.path)
                    put(EXTRA_LYRIC_FINGERPRINT, it.fingerprint)
                }
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

    // ── 播放 ──

    // 播放统一走本机回环代理：播放器拿到的地址里没有凭据，字节顺便沉淀成本地缓存。
    override suspend fun getMedia(song: LAudio): MediaData? {
        val activeProxy = proxy ?: return null
        val key = cacheKeyOf(song.id)
        val url = activeProxy.audioUrl(key) ?: return null
        // 开播即开始上报缓冲进度：先记下总长度，之后由缓存增长回调刷新已缓冲字节
        targetOf(song)?.let { target ->
            mutableBufferSnapshot.value = WebDavBufferSnapshot(
                key = key,
                filled = cache?.filledSize(key) ?: 0L,
                total = target.totalSize,
            )
        }
        return MediaData.Url(url)
    }

    /**
     * 当前播放项的缓冲进度：缓存覆盖率（已缓存字节 / 远端声明的总长）。
     *
     * 用覆盖率而不是播放器的内部缓冲：经代理播放时字节是本数据源在下载的，覆盖率才是
     * "还有多久能听"的准确信号。远端没报总长度时发 null，由 UI 按"未知"处理。
     */
    override fun bufferProgress(audioId: String): Flow<Float?> {
        val key = cacheKeyOf(audioId)
        return mutableBufferSnapshot
            .filter { it?.key == key }
            .map { snapshot -> snapshot?.fraction }
    }

    /** 缓存增长后刷新"正在播的那首"的已缓冲字节；其它歌的下载不影响当前进度显示。 */
    private fun publishBufferProgress(key: String) {
        val current = mutableBufferSnapshot.value ?: return
        if (current.key != key) return
        mutableBufferSnapshot.value = current.copy(filled = cache?.filledSize(key) ?: current.filled)
    }

    /**
     * 封面优先级：同目录的独立封面图（首扫就从 PROPFIND 拿到路径，零额外请求）→ 内嵌封面。
     *
     * 独立封面图往往是用户精心放的整张专辑图，质量高于内嵌缩略图；两者都取不到时返回 null，
     * 交给上层继续找同目录图片。
     */
    override suspend fun getPicture(song: LAudio, options: MediaFetchOptions): MediaData? {
        val key = cacheKeyOf(song.id)
        sidecarBytes(song, key, WebDavSidecarKind.COVER)?.let { return MediaData.Bytes(it) }
        val bytes = metadataStore?.readCover(key) ?: return null
        return MediaData.Bytes(bytes)
    }

    /**
     * 歌词优先级：同名 `.lrc` 边车文件 → 音频内嵌歌词。
     *
     * 内嵌歌词只有等整首缓存下来才能读到（taglib 需要本地文件），因此 `.lrc` 是唯一
     * "不用下载整首就能显示歌词"的来源——这正是本数据源首扫只做目录遍历的代价补偿。
     */
    override suspend fun getLyric(song: LAudio): String? {
        val key = cacheKeyOf(song.id)
        sidecarBytes(song, key, WebDavSidecarKind.LYRIC)
            ?.decodeToString()
            ?.removePrefix("\uFEFF")
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        val localPath = cache?.localPath(key) ?: return null
        return runCatching { Taglib.getLyric(localPath) }.getOrNull()?.takeIf(String::isNotBlank)
    }

    /** 拉取并缓存同目录边车文件；没有边车路径（首扫未发现）时返回 null。 */
    private suspend fun sidecarBytes(
        song: LAudio,
        key: String,
        kind: WebDavSidecarKind,
    ): ByteArray? {
        val extra = song.extra
        val path = when (kind) {
            WebDavSidecarKind.COVER -> extra?.get(EXTRA_COVER_PATH)
            WebDavSidecarKind.LYRIC -> extra?.get(EXTRA_LYRIC_PATH)
        }?.takeIf(String::isNotBlank) ?: return null

        val fingerprint = when (kind) {
            WebDavSidecarKind.COVER -> extra?.get(EXTRA_COVER_FINGERPRINT)
            WebDavSidecarKind.LYRIC -> extra?.get(EXTRA_LYRIC_FINGERPRINT)
        }
        val store = metadataStore ?: return null
        return store.sidecar(key, kind, fingerprint) {
            client?.download(path)
        }
    }

    // ── 装配 ──

    private suspend fun ensureExtractor(): WebDavExtractor {
        extractor?.let { return it }
        return setupMutex.withLock {
            extractor?.let { return@withLock it }
            val (cache, store) = ensureCacheInfrastructure()
            val created = WebDavExtractor(
                cache = cache,
                store = store,
                targets = TargetResolver(),
                onRecord = { target, record -> publishRecord(target, record) },
                onFlush = { batch -> mergeIntoSnapshot(batch) },
                progressState = mutableProgress,
            )
            extractor = created
            created
        }
    }

    private suspend fun ensureProxy(): WebDavProxy {
        proxy?.let { return it }
        return setupMutex.withLock {
            proxy?.let { return@withLock it }
            val (cache, _) = ensureCacheInfrastructure()
            val created = WebDavProxy(
                cache = cache,
                backend = ProxyBackend(),
                onCacheProgress = { key ->
                    extractor?.request(key)
                    publishBufferProgress(key)
                },
                // 每次需要时读配置：改配额立即生效，不用重建代理
                quotaBytes = { activeConfig.cacheQuotaBytes },
            )
            created.start()
            proxy = created
            created
        }
    }

    private fun ensureCacheInfrastructure(): Pair<WebDavCache, WebDavMetadataStore> {
        val cacheRoot = cacheRootProvider.cacheRoot()
            ?: throw WebDavException.Unexpected("当前平台不支持 WebDAV 数据源")
        val cache = WebDavCache(cacheRoot, json).also { this.cache = it }
        val store = metadataStore ?: WebDavMetadataStore(
            cacheRoot = cacheRoot,
            json = json,
            // 远端层用提供者：开关随时可切，重建 store 会丢掉内存层并让提取器指向旧对象。
            // 网络被门控拦住时直接返回 null——上传与远端读取同样是后台流量，必须一起停下；
            // 记录先留在本地，网络恢复后由增量同步补传。
            remoteProvider = { if (blockedByNetwork()) null else metadataRemote },
            onRemoteError = ::reportRemoteFailure,
        ).also {
            it.ensureReady()
            metadataStore = it
        }
        return cache to store
    }

    /** 把提取结果转成歌曲更新：额外字段合并进原有 extra，然后逐条入库。 */
    private suspend fun publishRecord(target: WebDavExtractionTarget, record: WebDavMetadataRecord) {
        val merged = target.audio.copy(
            title = record.title.takeIf(String::isNotBlank) ?: target.audio.title,
            extra = target.audio.extra.orEmpty() + record.toAudioExtra(
                sourceExtra = target.audio.extra.orEmpty(),
            ),
        )
        patchFlow.emit(merged)
    }

    /**
     * 把一批提取结果合并回完整快照。
     *
     * 必须做：下一次全量对账用的是内存里的快照，不合并的话刚补全的元数据会被旧数据覆盖回去。
     * 就地按 id 替换（publishUpdate 与 succeed 一致，重复 id 保留先出现者）。
     */
    private suspend fun mergeIntoSnapshot(batch: Map<String, WebDavMetadataRecord>) {
        val updated = stateStore.publishUpdate { audios ->
            audios.map { audio ->
                val record = batch[cacheKeyOf(audio.id)] ?: return@map audio
                audio.copy(
                    title = record.title.takeIf(String::isNotBlank) ?: audio.title,
                    extra = audio.extra.orEmpty() + record.toAudioExtra(
                        sourceExtra = audio.extra.orEmpty(),
                    ),
                )
            }
        } ?: return
        indexMutex.withLock {
            keyToAudio.clear()
            updated.audios.forEach { keyToAudio[cacheKeyOf(it.id)] = it }
        }
    }

    /**
     * 统计"库里有几首 / 已有几首提取记录"，用于进度展示。
     *
     * 刻意只查本地：这是每首歌一次的热路径，远端查询留给真正需要它的地方（后台补全），
     * 否则开关一开，首扫就会变成上万次小请求。
     */
    private suspend fun refreshLibraryCounts(audios: List<LAudio>) {
        val store = metadataStore ?: return
        val extracted = audios.count { audio ->
            val target = targetOf(audio) ?: return@count false
            store.has(cacheKeyOf(audio.id), target.fingerprint)
        }
        extractor?.updateLibraryCounts(total = audios.size, extracted = extracted)
    }

    /**
     * 允许后台主动下载时，把还没有完整元数据的歌曲依次补下来。
     *
     * 顺序执行（并发 1）而不是并发拉取：家用 NAS 与本机磁盘都不值得为后台补全付出瞬时压力。
     * 只读锁定在末尾，真正的网络约束来自 [blockedByNetwork]：非 Wi-Fi/以太网下直接不启动。
     */
    private fun startBackgroundFetch(audios: List<LAudio>) {
        backgroundJob?.cancel()
        backgroundJob = null
        if (!activeConfig.backgroundFetchEnabled) return
        if (blockedByNetwork()) {
            mutableBackgroundPaused.value = true
            return
        }

        backgroundJob = launch {
            val store = metadataStore ?: return@launch
            val activeProxy = proxy ?: return@launch
            for (audio in audios) {
                val target = targetOf(audio) ?: continue
                // 远端可能已经有这条记录（换设备/清过缓存）：查到就不必再把整首音频拉下来
                if (
                    store.has(
                        key = cacheKeyOf(audio.id),
                        fingerprint = target.fingerprint,
                        requireComplete = true,
                        consultRemote = activeConfig.metaSyncEnabled,
                    )
                ) {
                    continue
                }
                runCatchingCancellable {
                    activeProxy.ensureCached(
                        key = cacheKeyOf(audio.id),
                        target = WebDavStreamTarget(
                            path = target.remotePath,
                            totalSize = target.totalSize,
                            contentType = audio.extra?.get(EXTRA_CONTENT_TYPE),
                        ),
                        upTo = Long.MAX_VALUE,
                    )
                }
                extractor?.request(cacheKeyOf(audio.id))
            }
        }
    }

    private suspend fun targetOf(audio: LAudio): WebDavExtractionTarget? {
        val path = audio.extra?.get(EXTRA_PATH)?.takeIf(String::isNotBlank) ?: return null
        val size = audio.extra?.get(EXTRA_FILE_SIZE)?.toLongOrNull() ?: 0L
        return WebDavExtractionTarget(
            audio = audio,
            remotePath = path,
            totalSize = size,
            fingerprint = WebDavMetadataRecord.fingerprintOf(
                size = size,
                etag = audio.extra?.get(EXTRA_ETAG),
                lastModified = audio.extra?.get(EXTRA_LAST_MODIFIED),
            ),
        )
    }

    private inner class TargetResolver : WebDavExtractionTargets {
        override suspend fun targetOf(key: String): WebDavExtractionTarget? {
            val audio = indexMutex.withLock { keyToAudio[key] } ?: return null
            return this@WebDavSource.targetOf(audio)
        }
    }

    /** 传给代理的回调实现：只在数据源内部使用，因此不暴露成公开接口实现。 */
    private inner class ProxyBackend : WebDavProxyBackend {
        override suspend fun resolve(key: String): WebDavStreamTarget? {
            val audio = indexMutex.withLock { keyToAudio[key] } ?: return null
            val path = audio.extra?.get(EXTRA_PATH)?.takeIf(String::isNotBlank) ?: return null
            return WebDavStreamTarget(
                path = path,
                totalSize = audio.extra?.get(EXTRA_FILE_SIZE)?.toLongOrNull() ?: 0L,
                contentType = audio.extra?.get(EXTRA_CONTENT_TYPE),
            )
        }

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
