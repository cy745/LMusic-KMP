package com.lalilu.lmedia.source.filesystem

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SourceItem
import com.lalilu.lmedia.entity.buildSnapshot
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.source.MediaDataSource
import com.lalilu.lmedia.source.MediaSource
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.buffered
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.recover
import pro.respawn.flowmvi.plugins.reduce
import java.io.FileNotFoundException

@Stable
@Immutable
sealed interface AndroidFileSystemSourceState : MVIState {
    data object NotSelected : AndroidFileSystemSourceState
    abstract class Selected(open val path: String) : AndroidFileSystemSourceState
    abstract class Finished(override val path: String) : Selected(path = path)

    data class Scanning(
        val progress: () -> Float,
        val message: (() -> String)? = null,
        override val path: String
    ) : Selected(path = path)

    data class Success(val result: Snapshot, override val path: String) : Finished(path = path)
    data class Error(val error: Throwable, override val path: String) : Finished(path = path)
}


sealed interface AndroidFileSystemSourceIntent : MVIIntent {
    data class SelectFile(val path: String) : AndroidFileSystemSourceIntent
    data object CancelScanning : AndroidFileSystemSourceIntent
    data object ReStartScanning : AndroidFileSystemSourceIntent
}

private typealias Ctx = PipelineContext<AndroidFileSystemSourceState, AndroidFileSystemSourceIntent, MVIAction>

@SuppressLint("NewApi")
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidFileSystemSource(
    private val context: Application,
    lMediaKV: LMediaKV
) : MediaSource, MediaDataSource {
    private val scope = CoroutineScope(Dispatchers.Default)
    override val name: String = "AndroidFileSystemSource"
    private val filePath = lMediaKV.obtain<String>("file_path")
    private val stateFlow = MutableStateFlow(Snapshot.Loading)
    private var runningJob: Job? = null

    override fun source(): Flow<Snapshot> = stateFlow
    override val dataSource: MediaDataSource = this

    val store = store<AndroidFileSystemSourceState, AndroidFileSystemSourceIntent, MVIAction>(
        initial = AndroidFileSystemSourceState.NotSelected,
        scope = scope
    ) {
        configure {
            name = this@AndroidFileSystemSource.name
        }
        recover {
            updateState {
                AndroidFileSystemSourceState.Error(
                    error = it,
                    path = filePath.value
                )
            }
            null
        }
        reduce { intent ->
            when (intent) {
                is AndroidFileSystemSourceIntent.SelectFile -> {
                    runningJob?.cancel()
                    runningJob = launch { stateFlow.value = loadPath(path = intent.path) }
                }

                is AndroidFileSystemSourceIntent.ReStartScanning -> {
                    runningJob?.cancel()
                    runningJob = launch { stateFlow.value = loadPath(path = filePath.value) }
                }

                is AndroidFileSystemSourceIntent.CancelScanning -> {
                    runningJob?.cancel()
                    updateState {
                        AndroidFileSystemSourceState.Error(
                            error = RuntimeException("Cancel"),
                            path = filePath.value
                        )
                    }
                }
            }
        }
    }

    init {
        scope.launch {
            store.awaitStartup()
            store.intent(AndroidFileSystemSourceIntent.SelectFile(filePath.value))
        }
    }

    private suspend fun Ctx.loadPath(
        path: String
    ): Snapshot = withContext(Dispatchers.Unconfined) {
        val progressState = mutableStateOf(0f)
        val messageState = mutableStateOf("")

        filePath.value = path

        updateState {
            AndroidFileSystemSourceState.Scanning(
                progress = { progressState.value },
                message = { messageState.value },
                path = path
            )
        }

        val root = PlatformFile.fromBookmarkData(path.encodeToByteArray())
        val files = root.filterChildren { file ->
            if (file.isDirectory()) return@filterChildren false
            if (file.size() < 10) return@filterChildren false

            messageState.value = file.name
            MagicNumber.match(
                ext = file.extension,
                source = file.source().buffered()
            ) != null
        }

        val results = files.map { it.androidFile }.mapIndexed { index, file ->
            async(Dispatchers.io) {
                when (file) {
                    is AndroidFile.FileWrapper -> {
                        val metadata = Taglib.readMetadata(path = file.file.absolutePath)
                            ?: return@async null
                        ensureActive()

                        val newProgress = (index + 1).toFloat() / files.size.toFloat()
                        progressState.value = maxOf(progressState.value, newProgress)

                        messageState.value = metadata.title
                        SourceItem.FileItem(file.file) to metadata
                    }

                    is AndroidFile.UriWrapper -> {
                        val metadata = context.contentResolver
                            .openFileDescriptor(file.uri, "r")
                            ?.use { Taglib.readMetadata(fd = it.detachFd()) }
                            ?: return@async null
                        ensureActive()

                        val newProgress = (index + 1).toFloat() / files.size.toFloat()
                        progressState.value = maxOf(progressState.value, newProgress)

                        messageState.value = metadata.title
                        SourceItem.UriItem(file.uri) to metadata
                    }
                }
            }
        }

        val songs = results
            .awaitAll()
            .filterNotNull()
            .map { (source, metadata) ->
                LAudio(
                    id = source.key,
                    title = metadata.title,
                    subtitle = metadata.artist,
                    sourceItem = source,
                    metadata = metadata,
                    mediaSourceName = this@AndroidFileSystemSource.name
                )
            }

        songs.buildSnapshot().also {
            updateState {
                AndroidFileSystemSourceState.Success(
                    result = it,
                    path = path
                )
            }
        }
    }

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val audio = stateFlow.value.audios.firstOrNull { it.id == song.id }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")

        when (sourceItem) {
            is SourceItem.FileItem -> {
                val file = sourceItem.file

                val lyric = Taglib.getLyric(path = file.absolutePath)
                    ?: throw FileNotFoundException("Not found lyric for $file")

                lyric
            }

            is SourceItem.FilePathItem -> {
                val uri = sourceItem.path.toUri()

                val lyric = context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { Taglib.getLyric(fd = it.detachFd()) }
                    ?: throw FileNotFoundException("Not found lyric for $uri")

                lyric
            }

            is SourceItem.UriItem -> {
                val uri = sourceItem.uri

                val lyric = context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { Taglib.getLyric(fd = it.detachFd()) }
                    ?: throw FileNotFoundException("Not found lyric for $uri")

                lyric
            }

            else -> null
        }
    }

    override suspend fun getPicture(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val audio = stateFlow.value.audios.firstOrNull { it.id == song.id }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")

        when (sourceItem) {
            is SourceItem.FileItem -> {
                val file = sourceItem.file

                val picture = Taglib.getPicture(path = file.absolutePath)
                    ?: throw FileNotFoundException("Not found picture for $file")

                MediaData.Bytes(picture)
            }

            is SourceItem.FilePathItem -> {
                val uri = sourceItem.path.toUri()

                val picture = context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { Taglib.getPicture(fd = it.detachFd()) }
                    ?: throw FileNotFoundException("Not found picture for $uri")

                MediaData.Bytes(picture)
            }

            is SourceItem.UriItem -> {
                val uri = sourceItem.uri

                val picture = context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { Taglib.getPicture(fd = it.detachFd()) }
                    ?: throw FileNotFoundException("Not found picture for $uri")

                MediaData.Bytes(picture)
            }

            else -> null
        }
    }

    override suspend fun getMedia(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val audio = stateFlow.value.audios.firstOrNull { it.id == song.id }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")

        when (sourceItem) {
            is SourceItem.FileItem -> {
                val file = sourceItem.file
                MediaData.Bytes(file.readBytes())
            }

            is SourceItem.FilePathItem -> {
                MediaData.Url(sourceItem.path)
            }

            is SourceItem.UriItem -> {
                MediaData.Url(sourceItem.uri.toString())
            }

            else -> null
        }
    }
}

private suspend fun PlatformFile.filterChildren(
    block: suspend (file: PlatformFile) -> Boolean
): Collection<PlatformFile> = withContext(Dispatchers.io) {
    // 若不是文件夹，则无法遍历
    if (!this@filterChildren.isDirectory()) {
        // 若根元素即满足要求，且其不是文件夹，则直接返回根元素，否则直接返回空数组
        return@withContext if (block(this@filterChildren)) listOf(this@filterChildren) else emptyList()
    }

    val directory = mutableSetOf(this@filterChildren)
    val list = mutableSetOf<PlatformFile>()

    while (isActive && directory.isNotEmpty()) {
        val files = directory.map { it.list() }
            .flatten()

        val results = files
            .map { async { Triple(it, it.isDirectory(), block(it)) } }
            .awaitAll()

        directory.clear()
        for ((item, isDirectory, satisfy) in results) {
            if (isDirectory) directory.add(item)
            if (satisfy) list.add(item)
        }
    }
    list
}