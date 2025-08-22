package com.lalilu.lmedia.source

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import io.ktor.client.*
import io.ktor.client.request.*
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
    private var remoteMediaDataSource: MediaDataSource? = null

    /**
     * 客户端对象，使用Flow封装，当上游配置改变时，会重新创建客户端对象
     */
    private val rpcClientFlow = configFlow.flatMapLatest { config ->
        Logger.i(tag = TAG, messageString = "Config update: $config")

        if (!config.enable || config.url.isBlank()) {
            Logger.i(tag = TAG, messageString = "Invalid client config")
            return@flatMapLatest flowOf(null)
        }

        callbackFlow<KtorRpcClient?> {
            val client = HttpClient { installKrpc { serialization { json() } } }
                .rpc { url("ws://${config.url}/rpc") }

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
    }

    /**
     * 远程获取到的source的Flow，stateIn使其持久化，避免重复请求
     */
    val snapshotStateFlow = rpcClientFlow
        .flatMapLatest { client ->
            val service = client?.withService<MediaSourceBase>()
            remoteMediaDataSource = client?.withService<MediaDataSource>()

            service?.source()
                ?.catch { emit(Snapshot.Empty) }
                ?.onEach { it.audios.forEach { audio -> audio.mediaSourceName = this@RemoteSource.name } }
                ?: flowOf(Snapshot.Empty)
        }.stateIn(this, SharingStarted.Eagerly, Snapshot.Empty)

    override suspend fun getLyric(song: LAudio): String? = remoteMediaDataSource?.getLyric(song)
    override suspend fun getPicture(song: LAudio): MediaData? = remoteMediaDataSource?.getPicture(song)
    override suspend fun getMedia(song: LAudio): MediaData? = remoteMediaDataSource?.getMedia(song)

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