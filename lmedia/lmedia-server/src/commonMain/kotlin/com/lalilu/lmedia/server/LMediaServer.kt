package com.lalilu.lmedia.server

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.md5
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.server.entity.RemoteServerConfig
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

class LMediaServer(
    val config: RemoteServerConfig,
    val sources: PlatformMediaSource,
    val json: Json
) : KServer, KoinComponent {
    private var serverInstance: EngineServer? = null
    private suspend fun doInit() {
        val port = config.port
        val password = config.password
        val sourceName = config.sourceName

        val targetSource = sources.sources
            .firstOrNull { it.name == sourceName }

        require(config.port in 1024..65535) { "Invalid server config: [$port] port must be in range [1024, 65535]" }
        require(targetSource != null) { "No source found for name: $sourceName" }

        serverInstance = provideServer(
            port = port,
            mediaSource = { targetSource },
            serverConfig = config,
            config = { install(ContentNegotiation) { json(json) } }
        )
    }

    override suspend fun startAsync() {
        doInit()
        serverInstance?.start()
    }

    override suspend fun startSync() {
        doInit()
        serverInstance?.startSuspend(wait = true)
    }

    override suspend fun stopAndRelease() {
        serverInstance?.stopSuspend()
        serverInstance = null
    }
}

private fun provideServer(
    port: Int,
    mediaSource: () -> MediaSource?,
    serverConfig: RemoteServerConfig,
    config: Application.() -> Unit = {}
): EngineServer {
    suspend fun getAudioById(id: String): LAudio {

        return mediaSource()
            ?.source()
            ?.firstOrNull()?.audios
            ?.firstOrNull { it.id() == id }
            ?: throw IllegalArgumentException("No audio found for id: $id")
    }

    fun validateRequest(call: ApplicationCall): Boolean {
        val password = serverConfig.password
        if (password.isEmpty()) {
            // 无密码配置，跳过验证
            return true
        }
        val salt = call.request.queryParameters["s"]
        val token = call.request.queryParameters["t"]
        if (salt == null || token == null) {
            return false
        }
        val expectedToken = (password + salt).md5()
        return expectedToken == token
    }

    return embeddedServer(SERVER_ENGINE_FACTORY, port) {
        install(CORS) {
            anyHost()
            anyMethod()
        }

        routing {
            get("/") {
                if (!validateRequest(call)) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid or missing authentication")
                    return@get
                }
                call.respondText("Hello World!")
            }
            get("/source") {
                if (!validateRequest(call)) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid or missing authentication")
                    return@get
                }
                val mediaSource = mediaSource()
                    ?: throw IllegalArgumentException("No media source")
                call.respond<Snapshot>(mediaSource.source().firstOrNull() ?: Snapshot.Empty)
            }
            get("/lyric/{id}") {
                if (!validateRequest(call)) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid or missing authentication")
                    return@get
                }
                try {
                    val id = call.parameters["id"]
                        ?: throw IllegalArgumentException("Invalid id")
                    val mediaSource = mediaSource()
                        ?: throw IllegalArgumentException("No media source")

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
                if (!validateRequest(call)) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid or missing authentication")
                    return@get
                }
                try {
                    val id = call.parameters["id"]
                        ?: throw IllegalArgumentException("Invalid id")
                    val mediaSource = mediaSource()
                        ?: throw IllegalArgumentException("No media source")

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
                if (!validateRequest(call)) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid or missing authentication")
                    return@get
                }
                try {
                    val id = call.parameters["id"]
                        ?: throw IllegalArgumentException("Invalid id")
                    val mediaSource = mediaSource()
                        ?: throw IllegalArgumentException("No media source")

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