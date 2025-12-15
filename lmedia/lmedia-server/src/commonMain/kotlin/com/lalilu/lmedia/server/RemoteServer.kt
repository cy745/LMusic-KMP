package com.lalilu.lmedia.server

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.remote.RemoteServerConfig
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.source.MediaSource
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

typealias EngineFactory = ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration>
typealias EngineServer = EmbeddedServer<ApplicationEngine, out ApplicationEngine.Configuration>

expect val serverEngineFactory: EngineFactory?


@OptIn(ExperimentalCoroutinesApi::class)
@Single(createdAtStart = true)
class RemoteServer(
    lMediaKV: LMediaKV,
    platformMediaSource: PlatformMediaSource,
    json: Json
) : CoroutineScope {
    private val TAG = "RemoteServer"
    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            Logger.e(TAG, throwable)
        }

    /**
     * 筛选本机中可供外部远程访问的数据源
     */
    val remotableMediaSource by lazy { platformMediaSource.sources }

    /**
     * 服务器配置参数
     */
    val configItem = lMediaKV.obtain<RemoteServerConfig>(
        key = "REMOTE_SERVER_CONFIG",
        defaultValue = RemoteServerConfig.Empty
    )

    val configFlow = configItem.flow()

    /**
     * 服务器对象，使用Flow封装，当上游配置改变时，会重新创建服务器对象
     */
    val serverFlow = configFlow.flatMapLatest { config ->
        val targetMediaSource = remotableMediaSource
            .firstOrNull { it.name == config.selectedSourceKey }

        if (!config.enable) {
            return@flatMapLatest flowOf(null)
        }

        if (config.port !in 1024..65535) {
            Logger.i(tag = TAG, messageString = "Invalid server config: port must be in range [1024, 65535]")
            return@flatMapLatest flowOf(null)
        }

        if (targetMediaSource == null) {
            Logger.i(tag = TAG, messageString = "Invalid server config: targetMediaSource not set")
            return@flatMapLatest flowOf(null)
        }

        callbackFlow<EngineServer?> {
            val server = provideRpcServer(
                port = config.port,
                mediaSource = targetMediaSource,
                config = { install(ContentNegotiation) { json(json) } }
            )?.startSuspend(wait = false)

            if (server != null) {
                Logger.i(
                    tag = TAG,
                    messageString = "New Server instance created: ${server.hashCode()}"
                )
            }

            send(server)

            awaitClose {
                server?.stop()
                Logger.i(
                    tag = TAG,
                    messageString = "Server instance stopped: ${server?.hashCode()}"
                )
            }
        }
    }.stateIn(this, SharingStarted.Eagerly, null)
}


private fun provideRpcServer(
    port: Int,
    mediaSource: MediaSource,
    config: Application.() -> Unit = {}
): EngineServer? {
    val factory = serverEngineFactory ?: return null

    suspend fun getAudioById(id: String): LAudio {
        return mediaSource.source()
            .firstOrNull()?.audios
            ?.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("No audio found for id: $id")
    }

    return embeddedServer(factory, port) {
        install(CORS) {
            anyHost()
            anyMethod()
        }

        routing {
            get("/") {
                call.respondText("Hello World!")
            }
            get("/source") {
                call.respond<Snapshot>(mediaSource.source().firstOrNull() ?: Snapshot.Empty)
            }
            get("/lyric/{id}") {
                try {
                    val id = call.parameters["id"]
                        ?: throw IllegalArgumentException("Invalid id")

                    val audio = getAudioById(id)
                    val lyric = mediaSource.dataSource.getLyric(audio)
                        ?: throw IllegalArgumentException("No lyric found for id: $id")

                    call.respondText(lyric)
                } catch (e: Exception) {
                    call.respondText(text = "${e.message}")
                    Logger.e(
                        tag = "RemoteServer",
                        messageString = "getLyric: ${e.message}",
                        throwable = e
                    )
                }
            }
            get("/picture/{id}") {
                try {
                    val id = call.parameters["id"]
                        ?: throw IllegalArgumentException("Invalid id")

                    val audio = getAudioById(id)
                    val picture = mediaSource.dataSource.getPicture(audio)

                    when (picture) {
                        is MediaData.Bytes -> call.respondBytes(picture.bytes, ContentType.Image.PNG)
                        is MediaData.Url -> call.respondText(picture.url, ContentType.Text.Plain)
                        else -> throw IllegalArgumentException("Invalid Picture type")
                    }
                } catch (e: Exception) {
                    call.respondText(text = "${e.message}")
                    Logger.e(
                        tag = "RemoteServer",
                        messageString = "getPicture: ${e.message}",
                        throwable = e
                    )
                }
            }
            get("/media/{id}") {
                try {
                    val id = call.parameters["id"]
                        ?: throw IllegalArgumentException("Invalid id")

                    val audio = getAudioById(id)
                    val media = mediaSource.dataSource.getMedia(audio)

                    when (media) {
                        is MediaData.Bytes -> call.respondBytes(media.bytes, ContentType.Audio.MPEG)
                        is MediaData.Url -> call.respondText(media.url, ContentType.Text.Plain)
                        else -> throw IllegalArgumentException("Invalid Media type")
                    }
                } catch (e: Exception) {
                    call.respondText(text = "${e.message}")
                    Logger.e(
                        tag = "RemoteServer",
                        messageString = "getMedia: ${e.message}",
                        throwable = e
                    )
                }
            }
        }

        config()
    }
}