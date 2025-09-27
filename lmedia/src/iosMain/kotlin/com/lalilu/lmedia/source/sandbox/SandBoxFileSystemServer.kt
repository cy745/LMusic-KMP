package com.lalilu.lmedia.source.sandbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.lmedia.generated.resources.Res
import com.lalilu.lmedia.rpc.EngineServer
import com.lalilu.lmedia.rpc.serverEngineFactory
import io.github.vinceglb.filekit.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
)

object SandBoxFileSystemServer : CoroutineScope, KoinComponent {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    var server: EmbeddedServer<*, *>? by mutableStateOf(null)
        private set
    private val json: Json by inject<Json>()
    private var mutex = Mutex()

    suspend fun start(
        onStart: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
        onSourceUpdate: () -> Unit = {},
    ) = withContext(Dispatchers.io) {
        runCatching {
            mutex.withLock {
                if (server != null) {
                    server?.stopSuspend()
                    server = null
                }

                provideSandBoxFileSystemServer(
                    port = 8096,
                    config = { install(ContentNegotiation) { json(json) } },
                    onMusicFolderUpdate = { onSourceUpdate() }
                )?.also { it.startSuspend(false) }?.let {
                    server = it
                    onStart()
                }
            }
        }.getOrElse {
            onError(it)
            Logger.i(
                tag = "SandBoxFileSystemSourceContent",
                messageString = "Error starting server",
                throwable = it
            )
        }
    }

    fun stop() {
        if (server == null) {
            return
        }

        launch(Dispatchers.io) {
            runCatching {
                mutex.withLock {
                    server?.stopSuspend()
                    server = null
                }
            }.getOrElse {
                Logger.i(
                    tag = "SandBoxFileSystemSourceContent",
                    messageString = "Error stopping server",
                    throwable = it
                )
            }
        }
    }

    @OptIn(InternalAPI::class, ExperimentalTime::class)
    private fun provideSandBoxFileSystemServer(
        port: Int,
        config: Application.() -> Unit = {},
        onMusicFolderUpdate: () -> Unit = {}
    ): EngineServer? {
        val factory = serverEngineFactory ?: return null
        val musicFolder = FileKit.filesDir

        return embeddedServer(factory, port) {
            install(CORS) {
                anyHost()
                anyMethod()
                allowNonSimpleContentTypes = true
            }

            config()

            routing {
                get("/") {
                    call.respondBytes(
                        contentType = ContentType.Text.Html,
                        bytes = Res.readBytes("files/index.html")
                    )
                }

                // 获取文件列表
                get("/api/files") {
                    try {
                        val files = musicFolder.list().map { file ->
                            FileInfo(
                                name = file.name,
                                path = file.path,
                                size = if (file.isDirectory()) 0 else file.size(),
                                isDirectory = file.isDirectory()
                            )
                        }
                        call.respond(files)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to list files: ${e.message}")
                    }
                }

                // 删除文件
                delete("/api/files/{fileName}") {
                    try {
                        val fileName =
                            call.parameters["fileName"] ?: throw IllegalArgumentException("File name is required")
                        val file = musicFolder.resolve(fileName)
                        if (file.exists()) {
                            file.delete()
                            call.respond("File deleted successfully")
                            onMusicFolderUpdate()
                        } else {
                            call.respond(HttpStatusCode.NotFound, "File not found")
                        }
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to delete file: ${e.message}")
                    }
                }

                // 上传文件
                post("/api/files") {
                    try {
                        val multipart = call.receiveCIOMultipartData()
                        var fileWrote = false

                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FileItem -> {
                                    val fileName = part.originalFileName
                                        ?: "uploaded_file-${Clock.System.now().toEpochMilliseconds()}"
                                    val file = musicFolder.resolve(fileName)
                                    part.provider().copyTo(file.sink().asByteWriteChannel())
                                    call.respond("File uploaded successfully: ${file.absolutePath()}")
                                    fileWrote = true
                                }

                                else -> {}
                            }
                            part.dispose()
                        }

                        if (!fileWrote) {
                            call.respond(HttpStatusCode.BadRequest, "File data is missing")
                        }

                        onMusicFolderUpdate()
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to upload file: ${e.message}")
                        e.printStackTrace()
                    }
                }

                get("/api/files/{fileName}") {
                    val fileName =
                        call.parameters["fileName"] ?: throw IllegalArgumentException("File name is required")
                    val file = musicFolder.resolve(fileName)
                    if (file.exists()) {
                        call.respondBytes(
                            contentType = ContentType.Application.OctetStream,
                            status = HttpStatusCode.OK
                        ) { file.readBytes() }
                    } else {
                        call.respond(HttpStatusCode.NotFound, "File not found")
                    }
                }
            }
        }
    }
}


/**
 * 获取MultipartData，ios的ktor server默认没有实现CIO的MultipartData解析，所以需要自行实现
 */
@OptIn(InternalAPI::class)
private suspend fun RoutingCall.receiveCIOMultipartData(
    formFieldLimit: Long = Long.MAX_VALUE
): MultiPartData {
    val isMultipart = request.isMultipart()
    if (!isMultipart) throw UnsupportedMediaTypeException(null)

    val contentType = request.header(HttpHeaders.ContentType)
        ?: throw UnsupportedMediaTypeException(null)
    val contentLength = request.header(HttpHeaders.ContentLength)
        ?.toLong()

    return CIOMultipartDataBase(
        coroutineContext = coroutineContext + Dispatchers.Unconfined,
        channel = receiveChannel(),
        contentType = contentType,
        contentLength = contentLength,
        formFieldLimit = formFieldLimit
    )
}