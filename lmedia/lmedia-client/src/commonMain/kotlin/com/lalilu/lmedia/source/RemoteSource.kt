package com.lalilu.lmedia.source

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
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
    private val saver: Saver,
) : MediaSource, MediaDataSource, Configurable, CoroutineScope {
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

    override val config: MediaSourceConfig = buildConfig(
        onConfigChange = ::onConfigChange,
        key = name,
        description = "连接其他开放的 Remote Server 实例",
        saver = saver,
    ) {
        property<String>("url")
        property<String>("password")
        property<String>("salt", visibleInUI = false)
        property<String>("token", visibleInUI = false)

        function<Unit>(
            key = "Connect",
            description = "连接远程媒体源",
            isAvailable = { state.value is SnapshotState.Idle },
        ).onCall { loadData() }

        function<Unit>(
            key = "Cancel",
            description = "取消当前执行任务",
            isAvailable = { state.value is SnapshotState.Loading },
        ).onCall { loadingJob?.cancel() }

        function<Unit>(
            key = "Reset",
            description = "重置远程媒体源",
            isAvailable = { state.value !is SnapshotState.Idle && state.value !is SnapshotState.Loading },
        ).onCall { reset() }

        function<Unit>(
            key = "Refresh",
            description = "刷新远程媒体源",
            isAvailable = { state.value !is SnapshotState.Idle && state.value !is SnapshotState.Loading },
        ).onCall { loadData() }
    }

    override fun onConfigChange() {
        client?.close()
        client = null
    }

    override fun init() = loadData(isInitialize = true)

    private suspend fun requireClient(): HttpClient = client ?: recreateHttpClient()

    private suspend fun recreateHttpClient(): HttpClient = withContext(Dispatchers.io) {
        client?.close()
        val url = config.get<String>("url").getOrThrow()
        require(url.isNotBlank()) { "请填写服务器地址" }

        HttpClient {
            defaultRequest { url("http://$url") }
            install(ContentNegotiation) { json(json) }
        }.also { client = it }
    }

    private fun reset() {
        client?.close()
        client = null
        loadingJob?.cancel()
        loadingJob = launch { stateStore.reset() }
        config.update { setter ->
            setter("salt", "")
            setter("token", "")
        }
    }

    private fun loadData(isInitialize: Boolean = false) {
        loadingJob?.cancel()
        loadingJob = launch {
            val taskId = stateStore.begin(if (isInitialize) "Restoring connection..." else "Loading...")
            try {
                updateAuthentication()
                val result = requireClient()
                    .get("/source") { appendAuthentication() }
                    .body<Snapshot>()

                val serverAddress = config.get<String>("url").getOrThrow()
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

    private fun updateAuthentication() {
        val password = config.get<String>("password").getOrNull().orEmpty()
        if (password.isBlank()) return

        val salt = Random.nextBytes(16).toHexString()
        val token = (password + salt).md5()
        config.update { setter ->
            setter("password", "")
            setter("salt", salt)
            setter("token", token)
        }
    }

    private fun HttpRequestBuilder.appendAuthentication() {
        val salt = config.get<String>("salt").getOrNull()?.takeIf(String::isNotBlank)
        val token = config.get<String>("token").getOrNull()?.takeIf(String::isNotBlank)
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
        val serverAddress = config.get<String>("url").getOrThrow()
        val salt = config.get<String>("salt").getOrNull()?.takeIf(String::isNotBlank)
        val token = config.get<String>("token").getOrNull()?.takeIf(String::isNotBlank)
        val auth = if (salt != null && token != null) "?s=$salt&t=$token" else ""
        return MediaData.Url("http://$serverAddress/media/${remoteId.encodeURLPathPart()}$auth")
    }
}
