package com.lalilu.lmedia.source.filesystem

import android.annotation.SuppressLint
import android.app.Application
import androidx.core.net.toUri
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
import com.lalilu.lmedia.source.Saver
import com.lalilu.lmedia.source.buildConfig
import com.lalilu.lmedia.task.FileScannerTask
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.buffered
import org.koin.core.annotation.Single
import java.io.FileNotFoundException

@SuppressLint("NewApi")
@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class AndroidFileSystemSource(
    private val context: Application,
    private val saver: Saver,
) : MediaSource, MediaDataSource, Configurable {
    override val name: String = "AndroidFileSystemSource"
    override val dataSource: MediaDataSource = this

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val stateStore = MediaSourceStateStore()
    override val state: StateFlow<SnapshotState> = stateStore.state
    override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    private var loadingJob: Job? = null

    override val config: MediaSourceConfig = buildConfig(
        onConfigChange = ::onConfigChange,
        key = name,
        name = "Android文件系统源",
        description = "选择文件夹后，通过文件系统扫描音频文件",
        saver = saver,
    ) {
        property<String>(key = "file_path").provide("")

        function<Unit>(
            key = "Cancel",
            description = "取消当前任务",
            isAvailable = { state.value is SnapshotState.Loading },
        ).onCall {
            Logger.i(tag = name, messageString = "On Cancel")
            loadingJob?.cancel()
        }

        function<Unit>(
            key = "Reset",
            description = "重置",
            isAvailable = { state.value !is SnapshotState.Loading },
        ).onCall {
            Logger.i(tag = name, messageString = "On Reset")
            loadingJob?.cancel()
            loadingJob = scope.launch { stateStore.reset() }
        }

        function<Unit>(
            key = "Rescan",
            description = "重新扫描",
            isAvailable = { state.value !is SnapshotState.Loading },
        ).onCall {
            Logger.i(tag = name, messageString = "On Rescan")
            refresh()
        }
    }

    private val filePath get() = config.get<String>("file_path").getOrThrow()

    override fun onConfigChange() = Unit

    override fun init() {
        if (filePath.isNotBlank()) refresh()
    }

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

    private suspend fun load(taskId: Long): List<LAudio> = withContext(Dispatchers.Unconfined) {
        val root = PlatformFile.fromBookmarkData(filePath.encodeToByteArray())
        val files = FileScannerTask(
            predicate = predicate@{ file ->
                if (file.isDirectory()) return@predicate false
                if (file.size() < 10) return@predicate false

                stateStore.updateLoading(taskId, file.name, 0f)
                MagicNumber.match(
                    ext = file.extension,
                    source = file.source().buffered(),
                ) != null
            }
        ).scan(root)

        val semaphore = Semaphore(8)
        files.map { it.androidFile }.mapIndexed { index, file ->
            async(Dispatchers.io) {
                semaphore.withPermit {
                    when (file) {
                        is AndroidFile.FileWrapper -> {
                            val metadata = Taglib.readMetadata(path = file.file.absolutePath)
                                ?: return@async null
                            ensureActive()
                            stateStore.updateLoading(
                                taskId = taskId,
                                message = metadata.title.orEmpty(),
                                progress = (index + 1).toFloat() / files.size.coerceAtLeast(1),
                            )
                            val uri = file.file.toUri().toString()
                            LAudio(
                                id = "${LAudio.ID_PREFIX}$uri",
                                title = metadata.title ?: "Unknown",
                                subtitle = metadata.artist ?: "Unknown",
                                mediaSourceName = name,
                                extra = metadata.toAudioExtra(mapOf("uri" to uri)),
                            )
                        }

                        is AndroidFile.UriWrapper -> {
                            val metadata = context.contentResolver
                                .openFileDescriptor(file.uri, "r")
                                ?.use { Taglib.readMetadata(fd = it.detachFd()) }
                                ?: return@async null
                            ensureActive()
                            stateStore.updateLoading(
                                taskId = taskId,
                                message = metadata.title.orEmpty(),
                                progress = (index + 1).toFloat() / files.size.coerceAtLeast(1),
                            )
                            val uri = file.uri.toString()
                            LAudio(
                                id = "${LAudio.ID_PREFIX}${uri.md5()}",
                                title = metadata.title ?: "Unknown",
                                subtitle = metadata.artist ?: "Unknown",
                                mediaSourceName = name,
                                extra = metadata.toAudioExtra(mapOf("uri" to uri)),
                            )
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val uriStr = song.extra?.get("uri") ?: return@withContext null
        val androidUri = uriStr.toUri()
        when (androidUri.scheme) {
            "content" -> context.contentResolver
                .openFileDescriptor(androidUri, "r")
                ?.use { Taglib.getLyric(fd = it.detachFd()) }

            else -> Taglib.getLyric(path = androidUri.path ?: uriStr)
        } ?: throw FileNotFoundException("Not found lyric for $uriStr")
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
        song.extra?.get("uri")?.let(MediaData::Url)
    }
}
