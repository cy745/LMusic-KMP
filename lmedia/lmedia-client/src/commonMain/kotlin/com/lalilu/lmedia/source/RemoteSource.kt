package com.lalilu.lmedia.source

import androidx.compose.runtime.getValue
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.domain.source.MediaData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random


@Single(createdAtStart = true)
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSource(
    private val json: Json,
    private val saver: com.lalilu.lmedia.source.Saver
) : com.lalilu.lmedia.source.MediaSource, com.lalilu.lmedia.domain.source.MediaDataSource, CoroutineScope {

    companion object {
        private const val TAG = "RemoteSource"
    }

    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Logger.e(tag = TAG, throwable = throwable, messageString = "${throwable.message}")
        }

    override val name: String = TAG


    private var client: HttpClient? = null
    private val snapshotFlow = MutableStateFlow(Snapshot.Idle)
    private var loadingJob: Job? = null

    override val config: MediaSourceConfig = buildConfig(
        key = name,
        description = "连接其他开放的Remote Server实例",
        saver = saver
    ) {
        property<String>("url")
        property<String>("password")
        property<String>("salt", visibleInUI = false)
        property<String>("token", visibleInUI = false)

        function<Unit>(
            key = "Connect",
            description = "连接远程媒体源",
            isAvailable = { snapshotFlow.value is SnapshotState.Idle }
        ).onCall {
            loadData()
        }

        function<Unit>(
            key = "Cancel",
            description = "取消当前执行任务",
            isAvailable = {
                snapshotFlow.value.state is SnapshotState.Loading
            }
        ).onCall {
            loadingJob?.cancel()
            loadingJob = null
        }

        function<Unit>(
            key = "Reset",
            description = "重置远程媒体源",
            isAvailable = { snapshotFlow.value !is SnapshotState.Idle }
        ).onCall {
            reset()
        }

        function<Unit>(
            key = "Refresh",
            description = "刷新远程媒体源",
            isAvailable = {
                snapshotFlow.value.let { it is SnapshotState.Success || it is SnapshotState.Empty || it is SnapshotState.Error }
            }
        ).onCall {
            loadData()
        }
    }

    private suspend fun requireClient(): HttpClient {
        client?.let { return it }
        return client ?: reCreateHttpClient()
    }


    override fun onConfigChange() {
        client?.close()
        client = null
    }

    override fun init() {
        loadData(isInitialize = true)
    }

    suspend fun reCreateHttpClient() = withContext(Dispatchers.io) {
        client?.close()
        val url = config.get<String>("url").getOrThrow()

        HttpClient {
            defaultRequest { url("http://${url}") }
            install(ContentNegotiation) { json(json = json) }
        }.also { client = it }
    }


    /**
     * 重置
     */
    fun reset() {
        client?.close()
        client = null
        loadingJob?.cancel()
        loadingJob = null
        snapshotFlow.value = Snapshot.Idle
        // 清除认证信息
        config.update { setter ->
            setter("salt", "")
            setter("token", "")
        }
    }

    /**
     * 加载数据
     *
     * @param isInitialize 是否是初始化阶段
     */
    fun loadData(
        isInitialize: Boolean = false
    ) {
        loadingJob?.cancel()
        loadingJob = launch(Dispatchers.io) {
            safeDoAsync(
                onError = {
                    // 如果是初始化阶段失败的，则重置为Idle状态
                    if (isInitialize) {
                        snapshotFlow.value = snapshotFlow.value.copy(state = SnapshotState.Idle)
                    }
                }
            ) {
                // 处理密码认证
                val password = config.get<String>("password").getOrNull() ?: ""
                if (password.isNotEmpty()) {
                    val salt = generateSalt()
                    val token = generateToken(password, salt)
                    config.update { setter ->
                        setter("password", "")  // 清除密码
                        setter("salt", salt)    // 保存 salt
                        setter("token", token)  // 保存 token
                    }
                }

                val client = requireClient()

                snapshotFlow.emit(Snapshot.Loading)

                ensureActive()
                val salt = config.get<String>("salt").getOrNull()
                val token = config.get<String>("token").getOrNull()
                val result = client
                    .get("/source") {
                        if (salt != null && token != null) {
                            parameter("s", salt)
                            parameter("t", token)
                        }
                    }
                    .body<Snapshot>()
                    .also { /* redirectToNewSource removed in domain model */ }

                ensureActive()
                snapshotFlow.emit(result)
            }
        }
    }

    private suspend fun safeDoAsync(
        onError: suspend (Throwable) -> Unit = {},
        action: suspend () -> Unit
    ) {
        runCatching { action() }.getOrElse {
            Logger.e(tag = TAG, messageString = "${it.message}", throwable = it)

            snapshotFlow.value = snapshotFlow.value.copy(
                state = SnapshotState.Error(message = "[$name]${it.message}")
            )

            onError(it)
        }
    }

    override suspend fun getLyric(song: LAudio): String? {
        val targetUrl = "lyric/${song.id.encodeURLPathPart()}"
        val salt = config.get<String>("salt").getOrNull()
        val token = config.get<String>("token").getOrNull()
        val lyric = requireClient()
            .get(targetUrl) {
                if (salt != null && token != null) {
                    parameter("s", salt)
                    parameter("t", token)
                }
            }
            .bodyAsText()

        return lyric
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val targetUrl = "picture/${song.id.encodeURLPathPart()}"
        val salt = config.get<String>("salt").getOrNull()
        val token = config.get<String>("token").getOrNull()

        val picture = requireClient()
            .get(targetUrl) {
                if (salt != null && token != null) {
                    parameter("s", salt)
                    parameter("t", token)
                }
            }
            .bodyAsBytes()
            .takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(picture)
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val targetUrl = "media/${song.id.encodeURLPathPart()}"
        val url = config.get<String>("url").getOrThrow()
        val salt = config.get<String>("salt").getOrNull()
        val token = config.get<String>("token").getOrNull()

        val baseUrl = "http://${url}/media/${song.id.encodeURLPathPart()}"
        val authParams = if (salt != null && token != null) "?s=$salt&t=$token" else ""
        return MediaData.Url(baseUrl + authParams)

        val media = requireClient()
            .get(targetUrl) {
                if (salt != null && token != null) {
                    parameter("s", salt)
                    parameter("t", token)
                }
            }
            .bodyAsBytes()
            .takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(media)
    }

    override val dataSource: MediaDataSource = this
    override fun source(): Flow<Snapshot> = snapshotFlow

    private fun generateSalt(): String {
        // 生成16字节的随机 salt
        return Random.nextBytes(16).toHexString()
    }

    private fun generateToken(password: String, salt: String): String {
        // 认证方式：md5(password + salt)
        return (password + salt).md5()
    }
}