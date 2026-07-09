package com.lalilu.lmedia.source.filesystem

import android.annotation.SuppressLint
import android.app.Application
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata as DomainMetadata
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.domain.source.buildSnapshot
import com.lalilu.lmedia.source.MediaSource
import com.lalilu.lmedia.source.MediaSourceConfig
import com.lalilu.lmedia.source.Saver
import com.lalilu.lmedia.source.buildConfig
import com.lalilu.lmedia.source.range
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.buffered
import org.koin.core.annotation.Single
import java.io.FileNotFoundException


@SuppressLint("NewApi")
@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class AndroidFileSystemSource(
    private val context: Application,
    private val saver: Saver
) : MediaSource, MediaDataSource {
    override val name: String = "AndroidFileSystemSource"
    override val dataSource: MediaDataSource = this
    private val scope = CoroutineScope(Dispatchers.Default)
    private val stateFlow = MutableStateFlow(Snapshot.Idle)

    override fun source(): Flow<Snapshot> = stateFlow

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
            isAvailable = { stateFlow.value.state is SnapshotState.Loading }
        ).onCall {
            Logger.i(tag = name, messageString = "On Cancel")
            loadingJob?.cancel()
        }

        function<Unit>(
            key = "Reset",
            description = "重置",
            isAvailable = { stateFlow.value.state !is SnapshotState.Loading }
        ).onCall {
            Logger.i(tag = name, messageString = "On Reset")
            stateFlow.value = Snapshot.Idle
        }

        function<Unit>(
            key = "Rescan",
            description = "重新扫描",
            isAvailable = { stateFlow.value.state !is SnapshotState.Loading }
        ).onCall {
            Logger.i(tag = name, messageString = "On Rescan")
            loadingJob?.cancel()
            loadingJob = scope.launch {
                stateFlow.value = load { stateFlow.value = it }
            }
        }
    }

    private val filePath get() = config.get<String>("file_path").getOrThrow()

    override fun onConfigChange() { }

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
            var currentMessage = "Loading..."
            var currentProgress = 0f

            fun updateLoadingState(message: String, progress: Float = 0f) {
                currentMessage = message
                currentProgress = maxOf(progress, currentProgress)
            }

            update(
                Snapshot(state = SnapshotState.Loading(
                    message = currentMessage,
                    progress = currentProgress
                ))
            )

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
                            val uri = file.file.toUri().toString()
                            LAudio(
                                id = "${LAudio.ID_PREFIX}${uri}",
                                title = metadata.title ?: "Unknown",
                                subtitle = metadata.artist ?: "Unknown Subs",
                                mediaSourceName = name,
                                metadata = DomainMetadata(
                                    title = metadata.title,
                                    album = metadata.album,
                                    artist = metadata.artist,
                                    albumArtist = metadata.albumArtist,
                                    composer = metadata.composer,
                                    lyricist = metadata.lyricist,
                                    comment = metadata.comment,
                                    genre = metadata.genre,
                                    track = metadata.track,
                                    disc = metadata.disc,
                                    date = metadata.date,
                                    duration = metadata.duration,
                                    dateAdded = metadata.dateAdded,
                                    dateModified = metadata.dateModified
                                ),
                                extra = mapOf("uri" to uri)
                            )
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
                            val uri = file.uri.toString()
                            LAudio(
                                id = "${LAudio.ID_PREFIX}$uri",
                                title = metadata.title ?: "Unknown",
                                subtitle = metadata.artist ?: "Unknown Subs",
                                mediaSourceName = name,
                                metadata = DomainMetadata(
                                    title = metadata.title,
                                    album = metadata.album,
                                    artist = metadata.artist,
                                    albumArtist = metadata.albumArtist,
                                    composer = metadata.composer,
                                    lyricist = metadata.lyricist,
                                    comment = metadata.comment,
                                    genre = metadata.genre,
                                    track = metadata.track,
                                    disc = metadata.disc,
                                    date = metadata.date,
                                    duration = metadata.duration,
                                    dateAdded = metadata.dateAdded,
                                    dateModified = metadata.dateModified
                                ),
                                extra = mapOf("uri" to uri)
                            )
                        }
                    }
                }
            }

            val songs = results.awaitAll().filterNotNull()
            buildSnapshot(songs)
        }.getOrElse {
            Snapshot(state = SnapshotState.Error(message = it.message ?: "Unknown error"))
        }
    }

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val uriStr = song.extra?.get("uri") ?: return@withContext null
        val androidUri = uriStr.toUri()
        val lyric = when (androidUri.scheme) {
            "content" -> context.contentResolver
                .openFileDescriptor(androidUri, "r")
                ?.use { Taglib.getLyric(fd = it.detachFd()) }
            else -> Taglib.getLyric(path = androidUri.path ?: uriStr)
        } ?: throw FileNotFoundException("Not found lyric for $uriStr")
        lyric
    }

    override suspend fun getPicture(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val uriStr = song.extra?.get("uri") ?: return@withContext null
        val androidUri = uriStr.toUri()
        val picture = when (androidUri.scheme) {
            "content" -> context.contentResolver
                .openFileDescriptor(androidUri, "r")
                ?.use { Taglib.getPicture(fd = it.detachFd()) }
            else -> Taglib.getPicture(path = androidUri.path ?: uriStr)
        } ?: throw FileNotFoundException("Not found picture for $uriStr")
        MediaData.Bytes(picture)
    }

    override suspend fun getMedia(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val uri = song.extra?.get("uri") ?: return@withContext null
        MediaData.Url(uri)
    }
}

private suspend fun PlatformFile.filterChildren(
    block: suspend (file: PlatformFile) -> Boolean
): Collection<PlatformFile> = withContext(Dispatchers.io) {
    if (!this@filterChildren.isDirectory()) {
        return@withContext if (block(this@filterChildren)) listOf(this@filterChildren) else emptyList()
    }

    val directory = mutableSetOf(this@filterChildren)
    val list = mutableSetOf<PlatformFile>()

    while (isActive && directory.isNotEmpty()) {
        val files = directory.map { it.list() }.flatten()
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
