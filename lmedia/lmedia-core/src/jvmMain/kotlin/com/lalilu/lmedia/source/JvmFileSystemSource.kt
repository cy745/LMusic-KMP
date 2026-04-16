package com.lalilu.lmedia.source

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.lalilu.common.ext.io
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.buffered
import java.io.FileNotFoundException


@OptIn(ExperimentalCoroutinesApi::class)
class JvmFileSystemSource(
    private val saver: Saver? = null
) : MediaSource, MediaDataSource {
    override val name: String = "JvmFileSystemSource"
    private val scope = CoroutineScope(Dispatchers.Default)
    private val stateFlow = MutableStateFlow(Snapshot.Loading)

    override fun source(): Flow<Snapshot> = stateFlow
    override val dataSource: MediaDataSource = this
    private val stateValue by stateFlow.toComposeState(scope)

    private var loadingJob: Job? = null

    private val logger = KotlinLogging.logger(name)

    override val config: MediaSourceConfig = buildConfig(
        key = name,
        name = "文件系统源",
        description = "选择文件夹后，通过文件系统扫描音频文件",
        saver = saver
    ) {
        property<String>(key = "file_path").provide("")

        function<Unit>(
            key = "Cancel",
            description = "取消当前任务",
            isAvailable = { stateValue.let { it is SnapshotState.Loading || it is SnapshotState.LoadingDynamic } }
        ).onCall {
            logger.info { "On Cancel" }
            loadingJob?.cancel()
        }

        function<Unit>(
            key = "Reset",
            description = "重置",
            isAvailable = { stateValue.let { it !is SnapshotState.Loading && it !is SnapshotState.LoadingDynamic } }
        ).onCall {
            logger.info { "On Reset" }
            stateFlow.value = stateFlow.value.copy(state = SnapshotState.Idle)
        }

        function<Unit>(
            key = "Rescan",
            description = "重新扫描",
            isAvailable = { stateValue.let { it !is SnapshotState.Loading && it !is SnapshotState.LoadingDynamic } }
        ).onCall {
            logger.info { "On Rescan" }

            loadingJob?.cancel()
            loadingJob = scope.launch {
                stateFlow.value = load { stateFlow.value = it }
            }
        }
    }

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
            val path = config.get<String>("file_path").getOrThrow()
            val messageState = mutableStateOf("Loading...")
            val progressState = mutableStateOf(0f)

            update(
                Snapshot(
                    state = SnapshotState.LoadingDynamic(
                        message = { messageState.value },
                        progress = { progressState.value }
                    )
                )
            )

            fun updateLoadingState(
                message: String,
                progress: Float = 0f
            ) {
                messageState.value = message
                progressState.value = maxOf(progress, progressState.value)
            }

            val root = PlatformFile.fromBookmarkData(path.encodeToByteArray())
            val files = root.filterChildren { file ->
                if (file.isDirectory()) return@filterChildren false
                if (file.size() < 10) return@filterChildren false

                updateLoadingState(message = file.name)
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
                    updateLoadingState(
                        message = metadata.title ?: "",
                        progress = newProgress
                    )
                    SourceItem.FileItem(file) to metadata
                }
            }

            val songs = results
                .awaitAll()
                .filterNotNull()
                .map { (source, metadata) ->
                    LAudio(
                        id = source.key,
                        title = metadata.title ?: "",
                        subtitle = metadata.artist ?: "",
                        sourceItem = source,
                        metadata = metadata,
                        mediaSourceName = this@JvmFileSystemSource.name
                    )
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