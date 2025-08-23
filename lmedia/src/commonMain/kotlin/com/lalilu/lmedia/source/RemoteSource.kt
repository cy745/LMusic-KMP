package com.lalilu.lmedia.source

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.rpc.krpc.ktor.client.KtorRpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Serializable
data class RemoteSourceConfig(
    val enable: Boolean = false,
    val url: String = "",
    val password: String = ""
) {
    companion object {
        val Empty = RemoteSourceConfig()
    }
}

@Suppress("UnusedFlow")
@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [RemoteSource::class])
class RemoteSource(
    lMediaKV: LMediaKV,
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
    private val configItem = lMediaKV.obtain<RemoteSourceConfig>(
        key = "REMOTE_CONFIG",
        defaultValue = RemoteSourceConfig.Empty
    )

    private val configFlow = configItem.flow()
    private var remoteClient: HttpClient? = null

    /**
     * 客户端对象，使用Flow封装，当上游配置改变时，会重新创建客户端对象
     */
    private val rpcClientFlow = configFlow.flatMapLatest { config ->
        Logger.i(tag = TAG, messageString = "Config update: $config")

        if (!config.enable || config.url.isBlank()) {
            Logger.i(tag = TAG, messageString = "Invalid client config")
            return@flatMapLatest flowOf(null)
        }

        callbackFlow<Pair<HttpClient, KtorRpcClient?>> {
            val client = HttpClient {
                defaultRequest { url("http://${config.url}") }
                installKrpc { serialization { json() } }
            }
            val krpcClient = client
                .rpc { url("ws://${config.url}/rpc") }

            send(client to krpcClient)
            Logger.i(
                tag = TAG,
                messageString = "New Client instance created: ${client.hashCode()}"
            )

            awaitClose {
                krpcClient.close()
                client.close()
                Logger.i(
                    tag = TAG,
                    messageString = "Client instance closed: ${client.hashCode()}"
                )
            }
        }
    }

    /**
     * 远程获取到的source的Flow，stateIn使其持久化，避免重复请求
     */
    val snapshotStateFlow = rpcClientFlow
        .flatMapLatest { pair ->
            remoteClient = pair?.first
            val krpcClient = pair?.second
            val service = krpcClient?.withService<MediaSourceBase>()

            service?.source()
                ?.catch { emit(Snapshot.Empty) }
                ?.onEach { it.audios.forEach { audio -> audio.mediaSourceName = this@RemoteSource.name } }
                ?: flowOf(Snapshot.Empty)
        }.stateIn(this, SharingStarted.Eagerly, Snapshot.Empty)

    override suspend fun getLyric(song: LAudio): String? {
        val targetUrl = "lyric/${song.id.encodeURLPathPart()}"
        val lyric = remoteClient?.get(targetUrl)
            ?.bodyAsText()

        return lyric
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val targetUrl = "picture/${song.id.encodeURLPathPart()}"

        val picture = remoteClient?.get(targetUrl)
            ?.bodyAsBytes()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(picture)
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val targetUrl = "media/${song.id.encodeURLPathPart()}"
        val media = remoteClient?.get(targetUrl)
            ?.bodyAsBytes()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return MediaData.Bytes(media)
    }

    override val dataSource: MediaDataSource = this
    override fun source(): Flow<Snapshot> = snapshotStateFlow

    @Composable
    override fun Content(modifier: Modifier) {
        val config by configFlow.collectAsState(RemoteSourceConfig.Empty)
        val enable = remember(config) { mutableStateOf(config.enable) }
        val url = remember(config) { mutableStateOf(config.url) }
        val password = remember(config) { mutableStateOf(config.password) }
        val edited = remember(config) {
            derivedStateOf { enable.value != config.enable || url.value != config.url || password.value != config.password }
        }
        val source by remember { source() }.collectAsState(
            initial = Snapshot.Empty,
            context = Dispatchers.io
        )

        RemoteSourceContent(
            modifier = modifier,
            title = name,
            url = url,
            password = password,
            enable = enable,
            enableUpdateConfig = { edited.value },
            itemsCount = source.audios.size,
            onUpdateConfig = {
                configItem.value = RemoteSourceConfig(
                    enable = enable.value,
                    url = url.value,
                    password = password.value
                )
            }
        )
    }
}