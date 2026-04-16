package com.lalilu.lmedia.server

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.oshai.kotlinlogging.KotlinLogging
import com.lalilu.common.ext.io
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
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    override val coroutineContext: CoroutineContext = Dispatchers.io + SupervisorJob()
    private val json: Json by inject<Json>()
    private var mutex = Mutex()
    var server: EmbeddedServer<*, *>? by mutableStateOf(null)
        private set
    var urls: List<String> by mutableStateOf(emptyList())
        private set

    suspend fun start(
        indexHtml: suspend () -> ByteArray = { "hello world".toByteArray() },
        onStart: (urls: List<String>) -> Unit = {},
        onError: (Throwable) -> Unit = {},
        onSourceUpdate: () -> Unit = {},
    ) = withContext(coroutineContext) {
        runCatching {
            mutex.withLock {
                if (server != null) {
                    server?.stopSuspend()
                    server = null
                }

                provideSandBoxFileSystemServer(
                    indexHtml = indexHtml,
                    config = { install(ContentNegotiation) { json(json) } },
                    onSourceUpdate = onSourceUpdate
                ).also { it.startSuspend(false) }.let {
                    server = it
                    val connectors = it.engine.resolvedConnectors()
                    urls = connectors.map { connector ->
                        val scheme = when (connector.type) {
                            ConnectorType.HTTP -> "http://"
                            ConnectorType.HTTPS -> "https://"
                            else -> ""
                        }
                        "$scheme${connector.host}:${connector.port}"
                    }
                    onStart(urls)
                }
            }
        }.getOrElse {
            onError(it)
            KotlinLogging.logger("SandBoxFileSystemSourceContent").error(it) { "Error starting server" }
        }
    }

    fun stop() {
        if (server == null) {
            return
        }

        launch {
            runCatching {
                mutex.withLock {
                    server?.stopSuspend()
                    server = null
                }
            }.getOrElse {
                KotlinLogging.logger("SandBoxFileSystemSourceContent").error(it) { "Error stopping server" }
            }
        }
    }

    @OptIn(InternalAPI::class, ExperimentalTime::class)
    private fun provideSandBoxFileSystemServer(
        port: Int = 0,
        indexHtml: suspend () -> ByteArray = { "hello world".toByteArray() },
        config: Application.() -> Unit = {},
        onSourceUpdate: () -> Unit = {}
    ): EngineServer {
        val musicFolder = FileKit.filesDir

        return embeddedServer(SERVER_ENGINE_FACTORY, port) {
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
                        bytes = indexHtml()
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
                            onSourceUpdate()
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

                        onSourceUpdate()
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