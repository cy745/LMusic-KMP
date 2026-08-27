package com.lalilu.lmedia.source.sandbox

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaSourceStateStore
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.entity.toAudioExtra
import com.lalilu.lmedia.source.Configurable
import com.lalilu.lmedia.source.MediaSourceConfig
import com.lalilu.lmedia.source.buildConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.annotation.Single
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class SandboxFileSystemSource : MediaSource, MediaDataSource, Configurable {
    override val name: String = "SandboxFileSystemSource"
    override val dataSource: MediaDataSource = this

    private val scope = CoroutineScope(Dispatchers.io + SupervisorJob())
    private val stateStore = MediaSourceStateStore()
    override val state: StateFlow<SnapshotState> = stateStore.state
    override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    override val contentState = stateStore.contentState
    private var loadingJob: Job? = null

    /** iOS Documents directory (for iTunes file sharing). */
    private val documentsPath: String? =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String)

    override val config: MediaSourceConfig = buildConfig(key = name) {
        function<Unit>(
            key = "Refresh",
            description = "Refresh the sandbox folder",
            isAvailable = { state.value !is SnapshotState.Loading },
        ).onCall { refresh() }
    }

    override fun init() = refresh()

    private fun refresh() {
        loadingJob?.cancel()
        loadingJob = scope.launch {
            val taskId = stateStore.begin()
            try {
                stateStore.succeed(taskId, load(taskId))
            } catch (cancelled: CancellationException) {
                stateStore.cancel(taskId)
                throw cancelled
            } catch (throwable: Throwable) {
                Logger.e(tag = name, throwable = throwable, messageString = "Scan failed")
                stateStore.fail(taskId, throwable.message ?: "Unknown error")
            }
        }
    }

    private suspend fun load(taskId: Long): List<LAudio> {
        val root = documentsPath ?: error("Documents directory unavailable")
        val paths = scanDirectory(root)
        return paths.mapIndexedNotNull { index, path ->
            currentCoroutineContext().ensureActive()
            val metadata = Taglib.readMetadata(path = path) ?: return@mapIndexedNotNull null
            stateStore.updateLoading(
                taskId = taskId,
                message = metadata.title.orEmpty(),
                progress = (index + 1).toFloat() / paths.size.coerceAtLeast(1),
            )
            val relativePath = path.substringAfter(root)
            LAudio(
                id = "${LAudio.ID_PREFIX}${relativePath.md5()}",
                title = metadata.title ?: "Unknown",
                subtitle = metadata.artist ?: "Unknown",
                mediaSourceName = name,
                extra = metadata.toAudioExtra(mapOf("path" to path)),
            )
        }
    }

    override suspend fun getLyric(song: LAudio): String? {
        val path = song.extra?.get("path")
            ?: throw IllegalArgumentException("Missing path: ${song.id}")
        return Taglib.getLyric(path = path)
            ?: throw FileNotFoundException("Not found lyric for $path")
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val path = song.extra?.get("path")
            ?: throw IllegalArgumentException("Missing path: ${song.id}")
        val bytes = Taglib.getPicture(path = path)
            ?: throw FileNotFoundException("Not found picture for $path")
        return MediaData.Bytes(bytes)
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val path = song.extra?.get("path")
            ?: throw IllegalArgumentException("Missing path: ${song.id}")
        return MediaData.Url(NSURL.fileURLWithPath(path).absoluteString!!)
    }

    private fun scanDirectory(
        dirPath: String,
        result: MutableList<String> = mutableListOf(),
    ): List<String> {
        val fileManager = NSFileManager.defaultManager
        val contents = fileManager.contentsOfDirectoryAtPath(dirPath, null) as? List<String>
            ?: return result

        for (file in contents) {
            if (file == "." || file == "..") continue
            val fullPath = "$dirPath/$file"
            if (!fileManager.fileExistsAtPath(fullPath)) continue

            val attributes = fileManager.attributesOfItemAtPath(fullPath, null)
            val isDirectory = (attributes?.get(NSFileType) as? String) == NSFileTypeDirectory
            if (isDirectory) {
                scanDirectory(fullPath, result)
                continue
            }

            val isAudio = runCatching {
                SystemFileSystem.source(Path(fullPath)).buffered().use { source ->
                    MagicNumber.match(file.substringAfterLast('.'), source) != null
                }
            }.getOrDefault(false)
            if (isAudio) result += fullPath
        }
        return result
    }
}
