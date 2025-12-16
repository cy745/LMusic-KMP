package com.lalilu.lmedia.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.kv.KVContext
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SourceItem
import com.lalilu.lmedia.entity.buildSnapshot
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
sealed interface FileSystemSourceState : MVIState {
    data object NotSelected : FileSystemSourceState
    abstract class Selected(open val path: String) : FileSystemSourceState
    abstract class Finished(override val path: String) : Selected(path = path)

    data class Scanning(
        val progress: () -> Float,
        val message: (() -> String)? = null,
        override val path: String
    ) : Selected(path = path)

    data class Success(val result: Snapshot, override val path: String) : Finished(path = path)
    data class Error(val error: Throwable, override val path: String) : Finished(path = path)
}


sealed interface FileSystemSourceIntent : MVIIntent {
    data class SelectFile(val path: String) : FileSystemSourceIntent
    data object CancelScanning : FileSystemSourceIntent
    data object ReStartScanning : FileSystemSourceIntent
}

private typealias Ctx = PipelineContext<FileSystemSourceState, FileSystemSourceIntent, MVIAction>

@OptIn(ExperimentalCoroutinesApi::class)
class JvmFileSystemSource(
    lMediaKV: KVContext
) : MediaSource, MediaDataSource {
    private val scope = CoroutineScope(Dispatchers.Default)
    override val name: String = "JvmFileSystemSource"
    val filePath = lMediaKV.obtain<String>("file_path")
    private val stateFlow = MutableStateFlow(Snapshot.Loading)
    private var runningJob: Job? = null

    override fun source(): Flow<Snapshot> = stateFlow
    override val dataSource: MediaDataSource = this

    val store = store<FileSystemSourceState, FileSystemSourceIntent, MVIAction>(
        initial = FileSystemSourceState.NotSelected,
        scope = scope
    ) {
        configure { name = this@JvmFileSystemSource.name }
        recover {
            Logger.e(throwable = it, tag = name, messageString = "Error")
            updateState {
                FileSystemSourceState.Error(
                    error = it,
                    path = filePath.value
                )
            }
            null
        }
        reduce { intent ->
            when (intent) {
                is FileSystemSourceIntent.SelectFile -> {
                    runningJob?.cancel()
                    runningJob = launch { stateFlow.value = loadPath(path = intent.path) }
                }

                is FileSystemSourceIntent.ReStartScanning -> {
                    runningJob?.cancel()
                    runningJob = launch { stateFlow.value = loadPath(path = filePath.value) }
                }

                is FileSystemSourceIntent.CancelScanning -> {
                    runningJob?.cancel()
                    updateState {
                        FileSystemSourceState.Error(
                            error = RuntimeException("Cancel"),
                            path = filePath.value
                        )
                    }
                }
            }
        }
    }

    override fun start() {
        scope.launch {
            store.awaitStartup()
            store.intent(FileSystemSourceIntent.SelectFile(filePath.value))
        }
    }

    private suspend fun Ctx.loadPath(
        path: String
    ): Snapshot = withContext(Dispatchers.Unconfined) {
        val progressState = mutableStateOf(0f)
        val messageState = mutableStateOf("")

        filePath.value = path

        updateState {
            FileSystemSourceState.Scanning(
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

        val results = files.map { it.file }.mapIndexed { index, file ->
            async(Dispatchers.io) {
                val metadata = Taglib.readMetadata(path = file.absolutePath)
                    ?: return@async null
                ensureActive()

                val newProgress = (index + 1).toFloat() / files.size.toFloat()
                progressState.value = maxOf(progressState.value, newProgress)

                messageState.value = metadata.title
                SourceItem.FileItem(file) to metadata
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
                    mediaSourceName = this@JvmFileSystemSource.name
                )
            }

        songs.buildSnapshot().also {
            updateState {
                FileSystemSourceState.Success(
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