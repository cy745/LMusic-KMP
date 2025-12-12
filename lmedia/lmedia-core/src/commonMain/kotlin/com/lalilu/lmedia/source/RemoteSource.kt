package com.lalilu.lmedia.source

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SourceItemDefaults
import com.lalilu.lmedia.remote.RemoteSourceConfig
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
import kotlin.coroutines.CoroutineContext


@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSource(
    lMediaKV: LMediaKV,
    json: Json
) : MediaSource, MediaDataSource, CoroutineScope {

    companion object {
        private const val TAG = "RemoteSource"
    }

    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            Logger.e(tag = TAG, throwable = throwable, messageString = "${throwable.message}")
        }

    override val name: String = TAG

    /**
     * 客户端配置参数
     */
    val configItem = lMediaKV.obtain<RemoteSourceConfig>(
        key = "REMOTE_CONFIG",
        defaultValue = RemoteSourceConfig.Empty
    )

    val configFlow = configItem.flow()

    /**
     * 客户端对象，使用Flow封装，当上游配置改变时，会重新创建客户端对象
     */
    private val clientFlow = configFlow.flatMapLatest { config ->
        Logger.i(tag = TAG, messageString = "Config update: $config")

        if (!config.enable || config.url.isBlank()) {
            Logger.i(tag = TAG, messageString = "Invalid client config")
            return@flatMapLatest flowOf(null)
        }

        callbackFlow<HttpClient> {
            val client = HttpClient {
                defaultRequest { url("http://${config.url}") }
                install(ContentNegotiation) { json(json = json) }
            }

            send(client)
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
        .flatMapLatest { client ->
            flow {
                // 先返回Loading，避免下游长时间等待
                emit(Snapshot.Loading)
                emit(
                    client?.get("/source")
                        ?.body<Snapshot>()
                        ?: Snapshot.Empty
                )
            }.onEach {
                // 重定向数据源至RemoteSource
                it.audios.forEach { audio ->
                    audio.mediaSourceName = this@RemoteSource.name
                }
                it.albums.forEach { album ->
                    album.items.forEach { audio ->
                        audio.mediaSourceName = this@RemoteSource.name
                    }
                }
                it.artists.forEach { artist ->
                    artist.items.forEach { audio ->
                        audio.mediaSourceName = this@RemoteSource.name
                    }
                }
                it.folders.forEach { folder ->
                    folder.items.forEach { audio ->
                        audio.mediaSourceName = this@RemoteSource.name
                    }
                }
                it.genres.forEach { genre ->
                    genre.items.forEach { audio ->
                        audio.mediaSourceName = this@RemoteSource.name
                    }
                }
            }
        }.stateIn(this, SharingStarted.Eagerly, Snapshot.Empty)

    override suspend fun getLyric(song: LAudio): String? {
        val targetUrl = "lyric/${song.id.encodeURLPathPart()}"
        val lyric = clientFlow.value?.get(targetUrl)
            ?.bodyAsText()

        return lyric
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val targetUrl = "picture/${song.id.encodeURLPathPart()}"

        val picture = clientFlow.value
            ?.get(targetUrl)
            ?.bodyAsBytes()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(picture)
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val targetUrl = "media/${song.id.encodeURLPathPart()}"

        if (song.sourceItem is SourceItemDefaults.RequestUrl) {
            return MediaData.Url("http://${configItem.value.url}/media/${song.id.encodeURLPathPart()}")
        }

        val media = clientFlow.value
            ?.get(targetUrl)
            ?.bodyAsBytes()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(media)
    }

    override val dataSource: MediaDataSource = this
    override fun source(): Flow<Snapshot> = snapshotStateFlow
}