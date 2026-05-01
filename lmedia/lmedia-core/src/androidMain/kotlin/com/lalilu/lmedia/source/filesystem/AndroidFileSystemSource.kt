package com.lalilu.lmedia.source.filesystem

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.*
import com.lalilu.lmedia.source.*
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.buffered
import java.io.FileNotFoundException


@SuppressLint("NewApi")
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidFileSystemSource(
    private val context: Application,
    private val saver: Saver
) : MediaSource, MediaDataSource {
    override val name: String = "AndroidFileSystemSource"
    private val scope = CoroutineScope(Dispatchers.Default)
    private val stateFlow = MutableStateFlow(Snapshot.Idle)

    override fun source(): Flow<Snapshot> = stateFlow
    override val dataSource: MediaDataSource = this
    private val stateValue by stateFlow.toComposeState(scope)

    private var loadingJob: Job? = null

    override val config: MediaSourceConfig = buildConfig(
        key = name,
        name = "Android文件系统源",
        description = "选择文件夹后，通过文件系统扫描音频文件",
        saver = saver
    ) {
        property<String>(key = "file_path").provide("")

        function<Unit>(
            key = "Cancel",
            description = "取消当前任务",
            isAvailable = { stateValue.let { it is SnapshotState.Loading || it is SnapshotState.LoadingDynamic } }
        ).onCall {
            Logger.i(tag = name, messageString = "On Cancel")
            loadingJob?.cancel()
        }

        function<Unit>(
            key = "Reset",
            description = "重置",
            isAvailable = { stateValue.let { it !is SnapshotState.Loading && it !is SnapshotState.LoadingDynamic } }
        ).onCall {
            Logger.i(tag = name, messageString = "On Reset")
            stateFlow.value = stateFlow.value.copy(state = SnapshotState.Idle)
        }

        function<Unit>(
            key = "Rescan",
            description = "重新扫描",
            isAvailable = { stateValue.let { it !is SnapshotState.Loading && it !is SnapshotState.LoadingDynamic } }
        ).onCall {
            Logger.i(tag = name, messageString = "On Rescan")

            loadingJob?.cancel()
            loadingJob = scope.launch {
                stateFlow.value = load { stateFlow.value = it }
            }
        }
    }

    private val filePath get() = config.get<String>("file_path").getOrThrow()

    override fun onConfigChange() {

    }

    override fun init() {
        loadingJob?.cancel()
        loadingJob = scope.launch {
            stateFlow.value = load { stateFlow.value = it }
        }
    }

    private suspend fun load(
        update: suspend (Snapshot) -> Unit = {}
    ): Snapshot = withContext(scope.coroutineContext) {
        runCatching {
            val messageState = mutableStateOf("Loading...")
            val progressState = mutableFloatStateOf(0f)

            update(
                Snapshot(
                    state = SnapshotState.LoadingDynamic(
                        message = { messageState.value },
                        progress = { progressState.floatValue }
                    )
                )
            )

            fun updateLoadingState(
                message: String,
                progress: Float = 0f
            ) {
                messageState.value = message
                progressState.floatValue = maxOf(progress, progressState.floatValue)
            }

            val root = PlatformFile.fromBookmarkData(filePath.encodeToByteArray())
            val files = root.filterChildren { file ->
                if (file.isDirectory()) return@filterChildren false
                if (file.size() < 10) return@filterChildren false

                updateLoadingState(message = file.name)
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
                            updateLoadingState(
                                message = metadata.title ?: "",
                                progress = newProgress
                            )
                            SourceItem.FileItem(file.file) to metadata
                        }

                        is AndroidFile.UriWrapper -> {
                            val metadata = context.contentResolver
                                .openFileDescriptor(file.uri, "r")
                                ?.use { Taglib.readMetadata(fd = it.detachFd()) }
                                ?: return@async null
                            ensureActive()

                            val newProgress = (index + 1).toFloat() / files.size.toFloat()
                            updateLoadingState(
                                message = metadata.title ?: "",
                                progress = newProgress
                            )
                            SourceItem.UriItem(file.uri) to metadata
                        }
                    }
                }
            }

            val songs = results
                .awaitAll()
                .filterNotNull()
                .map { (source, metadata) ->
                    buildAudio(id = source.key) {
                        title(metadata.title)
                        subtitle(metadata.artist)
                        source(source)
                        metadata(metadata)
                    }
                }

            songs.buildSnapshot()
        }.getOrElse {
            Snapshot(state = SnapshotState.Error(message = it.message ?: "Unknown error"))
        }
    }

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val audio = stateFlow.value.audios.firstOrNull { it.idValue() == song.idValue() }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.idValue()}")

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
        val audio = stateFlow.value.audios.firstOrNull { it.idValue() == song.idValue() }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.idValue()}")

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
        val audio = stateFlow.value.audios.firstOrNull { it.idValue() == song.idValue() }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.idValue()}")

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