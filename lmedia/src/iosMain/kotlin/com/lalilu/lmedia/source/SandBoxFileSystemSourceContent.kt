package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private var server: EmbeddedServer<*, *>? by mutableStateOf(null)

@Composable
fun SandBoxFileSystemSourceContent(
    modifier: Modifier = Modifier
) {
    val json = koinInject<Json>()

    LaunchedEffect(Unit) {
        delay(2000)
        if (isActive) {
            runCatching {
                val temp = provideSandBoxFileSystemServer(
                    port = 8096,
                    config = { install(ContentNegotiation) { json(json) } }
                )
                temp?.startSuspend(wait = false)
                server = temp
            }.getOrElse {
                Logger.i(
                    tag = "SandBoxFileSystemSourceContent",
                    messageString = "Error starting server",
                    throwable = it
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                server?.stop(1000, 1000)
                server = null
            }.getOrElse {
                Logger.i(
                    tag = "SandBoxFileSystemSourceContent",
                    messageString = "Error stopping server",
                    throwable = it
                )
            }
        }
    }

    Card(modifier = modifier) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(text = "SandBoxFileSystemSource, server status: isRunning: ${server != null}")
        }
    }
}

@Serializable
data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long = 0
)

@OptIn(InternalAPI::class, ExperimentalTime::class)
private fun provideSandBoxFileSystemServer(
    port: Int,
    config: Application.() -> Unit = {}
): EngineServer? {
    val factory = serverEngineFactory ?: return null
    val musicFolder = FileKit.filesDir

    return embeddedServer(factory, port) {
        this.environment.config

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
                                val fileName =
                                    part.originalFileName ?: "uploaded_file-${Clock.System.now().toEpochMilliseconds()}"
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
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to upload file: ${e.message}")
                    e.printStackTrace()
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
    formFieldLimit: Long = -1L
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