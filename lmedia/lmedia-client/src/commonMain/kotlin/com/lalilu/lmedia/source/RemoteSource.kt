package com.lalilu.lmedia.source

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.common.kv.KVItem
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaSourceStateStore
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/**
 * 新 Remote 协议只交换包含 audios 的完整成功 Snapshot。
 * 远端歌曲 ID 保存在 extra，应用层 ID 按服务器地址命名空间化，避免与本地来源冲突。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class RemoteSource(
    private val json: Json,
    kv: LMediaKV,
) : MediaSource, MediaDataSource, CoroutineScope {
    companion object {
        private const val TAG = "RemoteSource"
        private const val EXTRA_REMOTE_ID = "remoteId"
    }

    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Logger.e(tag = TAG, throwable = throwable, messageString = throwable.message.orEmpty())
        }

    override val name: String = TAG
    override val dataSource: MediaDataSource = this

    private val stateStore = MediaSourceStateStore()
    override val state: StateFlow<SnapshotState> = stateStore.state
    override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    override val contentState = stateStore.contentState
    private var client: HttpClient? = null
    private var loadingJob: Job? = null

    val config: KVItem<RemoteSourceConfig> = kv.obtain(
        key = "${name}Config",
        defaultValue = RemoteSourceConfig(),
    ).apply { disableAutoSave() }
    private var activeConfig: RemoteSourceConfig = config.value

    override fun init() {
        if (activeConfig.isConfigured) loadData(isInitialize = true)
    }

    /**
     * 保存连接配置并发起连接。密码为空时，仅在服务器地址未变化的情况下复用既有认证信息；
     * 切换服务器且不填写密码则按无密码服务连接。
     */
    fun connect(url: String, password: String): Result<Unit> = runCatching {
        val normalizedUrl = normalizeServerUrl(url)
        val current = activeConfig
        val canReuseAuthentication = password.isBlank() &&
            normalizedUrl == current.url &&
            current.salt.isNotBlank() &&
            current.token.isNotBlank()

        val (salt, token) = when {
            password.isNotBlank() -> {
                val newSalt = Random.nextBytes(16).toHexString()
                newSalt to (password + newSalt).md5()
            }

            canReuseAuthentication -> current.salt to current.token
            else -> "" to ""
        }

        config.value = RemoteSourceConfig(
            url = normalizedUrl,
            salt = salt,
            token = token,
        )
        config.save()
        activeConfig = config.value
        recreateHttpClient()
        loadData()
    }

    fun retry(): Result<Unit> = runCatching {
        require(activeConfig.isConfigured) { "请先填写服务器地址" }
        loadData()
    }

    fun cancel() {
        loadingJob?.cancel()
    }

    fun reset() {
        closeClient()
        loadingJob?.cancel()
        loadingJob = launch { stateStore.reset() }
        config.value = activeConfig.copy(salt = "", token = "")
        config.save()
        activeConfig = config.value
    }

    fun refresh() = retry()

    private suspend fun requireClient(): HttpClient = client ?: recreateHttpClient()

    private fun recreateHttpClient(): HttpClient {
        closeClient()
        val serverUrl = activeConfig.url
        require(serverUrl.isNotBlank()) { "请填写服务器地址" }

        return HttpClient {
            defaultRequest { url(serverUrl) }
            install(ContentNegotiation) { json(json) }
        }.also { client = it }
    }

    private fun closeClient() {
        client?.close()
        client = null
    }

    private fun loadData(isInitialize: Boolean = false) {
        loadingJob?.cancel()
        loadingJob = launch {
            val taskId = stateStore.begin(if (isInitialize) "Restoring connection..." else "Loading...")
            try {
                val result = requireClient()
                    .get("/source") { appendAuthentication() }
                    .body<Snapshot>()

                val serverAddress = activeConfig.url
                val audios = result.audios.map { audio ->
                    val remoteId = audio.id
                    audio.copy(
                        id = "${LAudio.ID_PREFIX}${"$serverAddress:$remoteId".md5()}",
                        mediaSourceName = name,
                        extra = audio.extra.orEmpty() + (EXTRA_REMOTE_ID to remoteId),
                    )
                }
                stateStore.succeed(taskId, audios)
            } catch (cancelled: CancellationException) {
                stateStore.cancel(taskId)
                throw cancelled
            } catch (throwable: Throwable) {
                Logger.e(tag = TAG, messageString = throwable.message.orEmpty(), throwable = throwable)
                if (isInitialize) {
                    stateStore.reset()
                } else {
                    stateStore.fail(taskId, "[$name]${throwable.message}")
                }
            }
        }
    }

    private fun HttpRequestBuilder.appendAuthentication() {
        val salt = activeConfig.salt.takeIf(String::isNotBlank)
        val token = activeConfig.token.takeIf(String::isNotBlank)
        if (salt != null && token != null) {
            parameter("s", salt)
            parameter("t", token)
        }
    }

    override suspend fun getLyric(song: LAudio): String? {
        val remoteId = song.extra?.get(EXTRA_REMOTE_ID) ?: return null
        return requireClient()
            .get("lyric/${remoteId.encodeURLPathPart()}") { appendAuthentication() }
            .bodyAsText()
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val remoteId = song.extra?.get(EXTRA_REMOTE_ID) ?: return null
        val response = requireClient()
            .get("picture/${remoteId.encodeURLPathPart()}") { appendAuthentication() }
        val bytes = response.bodyAsBytes().takeIf(ByteArray::isNotEmpty) ?: return null
        return MediaData.Bytes(bytes)
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val remoteId = song.extra?.get(EXTRA_REMOTE_ID) ?: return null
        val serverAddress = activeConfig.url.trimEnd('/')
        val salt = activeConfig.salt.takeIf(String::isNotBlank)
        val token = activeConfig.token.takeIf(String::isNotBlank)
        val auth = if (salt != null && token != null) "?s=$salt&t=$token" else ""
        return MediaData.Url("$serverAddress/media/${remoteId.encodeURLPathPart()}$auth")
    }

    private fun normalizeServerUrl(value: String): String {
        val url = value.trim()
        require(url.isNotBlank()) { "请填写服务器地址" }
        val withScheme = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "http://$url"
        }
        return withScheme.trimEnd('/')
    }
}
