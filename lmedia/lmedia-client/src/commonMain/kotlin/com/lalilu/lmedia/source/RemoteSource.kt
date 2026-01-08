package com.lalilu.lmedia.source

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


@Single(createdAtStart = true)
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSource(
    json: Json
) : MediaSource, MediaDataSource, CoroutineScope {

    companion object {
        private const val TAG = "RemoteSource"
    }

    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Logger.e(tag = TAG, throwable = throwable, messageString = "${throwable.message}")
        }

    override val name: String = TAG
    override val config: MediaSourceConfig = buildConfig(name) {
        property<String>("url")
        property<String>("password")

        function<Unit>("Reset").onCall {

        }
    }

    private val url = config.get<String>("url").getOrElse { "" }

    override fun onConfigChange() {

    }

    /**
     * 客户端对象，使用Flow封装，当上游配置改变时，会重新创建客户端对象
     */
    private val clientFlow = config.holder.flatMapLatest { config ->
        Logger.i(tag = TAG, messageString = "Config update: $config")

        if (url.isBlank()) {
            Logger.i(tag = TAG, messageString = "Invalid client config")
            return@flatMapLatest flowOf(
                Result.failure(IllegalArgumentException("Invalid client config"))
            )
        }

        callbackFlow {
            val client = HttpClient {
                defaultRequest { url("http://${url}") }
                install(ContentNegotiation) { json(json = json) }
            }

            send(Result.success(client))
            Logger.i(
                tag = TAG,
                messageString = "New Client instance created: ${client.hashCode()}"
            )

            awaitClose {
                client.close()
                Logger.i(
                    tag = TAG,
                    messageString = "Client instance closed: ${client.hashCode()}"
                )
            }
        }
    }.stateIn(this, SharingStarted.Eagerly, null)

    /**
     * 远程获取到的source的Flow，stateIn使其持久化，避免重复请求
     */
    val snapshotStateFlow = clientFlow
        .flatMapLatest { result ->
            flow {
                val client = result?.getOrElse { error ->
                    emit(Snapshot(state = SnapshotState.Error(message = "[$name]${error.message}")))
                    null
                } ?: return@flow

                // 先返回Loading，避免下游长时间等待
                emit(Snapshot.Loading)
                emit(
                    client
                        .get("/source")
                        .body<Snapshot>()
                )
            }.onEach {
                // 重定向数据源至RemoteSource
                it.redirectToNewSource(this@RemoteSource.name)
            }
        }.stateIn(this, SharingStarted.Eagerly, Snapshot.Empty)

    override suspend fun getLyric(song: LAudio): String? {
        val targetUrl = "lyric/${song.id.encodeURLPathPart()}"
        val lyric = clientFlow.value
            ?.getOrNull()
            ?.get(targetUrl)
            ?.bodyAsText()

        return lyric
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val targetUrl = "picture/${song.id.encodeURLPathPart()}"

        val picture = clientFlow.value
            ?.getOrNull()
            ?.get(targetUrl)
            ?.bodyAsBytes()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(picture)
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val targetUrl = "media/${song.id.encodeURLPathPart()}"

        if (song.sourceItem is SourceItemDefaults.RequestUrl) {
            return MediaData.Url("http://${url}/media/${song.id.encodeURLPathPart()}")
        }

        val media = clientFlow.value
            ?.getOrNull()
            ?.get(targetUrl)
            ?.bodyAsBytes()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(media)
    }

    override val dataSource: MediaDataSource = this
    override fun source(): Flow<Snapshot> = snapshotStateFlow
}