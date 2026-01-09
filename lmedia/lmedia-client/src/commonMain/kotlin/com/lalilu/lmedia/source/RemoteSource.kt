package com.lalilu.lmedia.source

import androidx.compose.runtime.getValue
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.*
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


@Single(createdAtStart = true)
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSource(
    private val json: Json
) : MediaSource, MediaDataSource, CoroutineScope {

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
    private val stateValue by snapshotFlow.toComposeState(this)

    override val config: MediaSourceConfig = buildConfig(
        key = name,
        description = "连接其他开放的Remote Server实例"
    ) {
        property<String>("url")
        property<String>("password")

        function<Unit>(
            key = "Connect",
            description = "连接远程媒体源",
            isAvailable = { stateValue is SnapshotState.Idle }
        ).onCall {
            loadData()
        }

        function<Unit>(
            key = "Cancel",
            description = "取消当前执行任务",
            isAvailable = {
                stateValue.let { it is SnapshotState.Loading || it is SnapshotState.LoadingDynamic }
            }
        ).onCall {
            loadData()
        }

        function<Unit>(
            key = "Reset",
            description = "重置远程媒体源",
            isAvailable = { stateValue !is SnapshotState.Idle }
        ).onCall {
            client?.close()
            client = null
            snapshotFlow.value = Snapshot.Idle
        }

        function<Unit>(
            key = "Refresh",
            description = "刷新远程媒体源",
            isAvailable = {
                stateValue.let { it is SnapshotState.Success || it is SnapshotState.Empty || it is SnapshotState.Error }
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
     * 加载数据
     *
     * @param isInitialize 是否是初始化阶段
     */
    fun loadData(
        isInitialize: Boolean = false
    ) = launch(Dispatchers.io) {
        safeDoAsync(
            onError = {
                // 如果是初始化阶段失败的，则重置为Idle状态
                if (isInitialize) {
                    snapshotFlow.value = snapshotFlow.value.copy(state = SnapshotState.Idle)
                }
            }
        ) {
            val client = requireClient()

            snapshotFlow.emit(Snapshot.Loading)

            ensureActive()
            val result = client
                .get("/source")
                .body<Snapshot>()
                .also { it.redirectToNewSource(this@RemoteSource.name) }

            ensureActive()
            snapshotFlow.emit(result)
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
        val lyric = requireClient()
            .get(targetUrl)
            .bodyAsText()

        return lyric
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val targetUrl = "picture/${song.id.encodeURLPathPart()}"

        val picture = requireClient()
            .get(targetUrl)
            .bodyAsBytes()
            .takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(picture)
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val targetUrl = "media/${song.id.encodeURLPathPart()}"
        val url = config.get<String>("url").getOrThrow()

        if (song.sourceItem is SourceItemDefaults.RequestUrl) {
            return MediaData.Url("http://${url}/media/${song.id.encodeURLPathPart()}")
        }

        val media = requireClient()
            .get(targetUrl)
            .bodyAsBytes()
            .takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(media)
    }

    override val dataSource: MediaDataSource = this
    override fun source(): Flow<Snapshot> = snapshotFlow
}