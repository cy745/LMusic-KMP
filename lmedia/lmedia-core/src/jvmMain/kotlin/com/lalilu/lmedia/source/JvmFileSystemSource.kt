package com.lalilu.lmedia.source

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.common.kv.KVItem
import com.lalilu.lmedia.LMediaKV
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
import com.lalilu.lmedia.task.FileScannerTask
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.buffered
import org.koin.core.annotation.Single
import java.io.FileNotFoundException

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class JvmFileSystemSource(
    kv: LMediaKV,
) : MediaSource, MediaDataSource {
    // 已用于配置前缀和数据库来源归属；虽然名称不够准确，但不能在无迁移时直接改动。
    override val name: String = "AndroidFileSystemSource"
    override val dataSource: MediaDataSource = this

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val stateStore = MediaSourceStateStore()
    override val state: StateFlow<SnapshotState> = stateStore.state
    override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    override val contentState = stateStore.contentState
    private var loadingJob: Job? = null

    val config: KVItem<FileSystemSourceConfig> = kv.obtain(
        key = "${name}Config",
        defaultValue = FileSystemSourceConfig(),
    ).apply { disableAutoSave() }

    private val filePath: String
        get() = config.value.directoryBookmark

    override fun init() {
        if (filePath.isNotBlank()) refresh()
    }

    fun selectDirectory(bookmark: String) {
        config.value = config.value.copy(directoryBookmark = bookmark)
        config.save()
        refresh()
    }

    fun cancel() {
        Logger.i(tag = name, messageString = "Cancel requested")
        loadingJob?.cancel()
    }

    fun reset() {
        Logger.i(tag = name, messageString = "Reset requested")
        loadingJob?.cancel()
        loadingJob = scope.launch { stateStore.reset() }
    }

    fun refresh() {
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
        files.mapIndexed { index, file ->
            async(Dispatchers.io) {
                semaphore.withPermit {
                    val metadata = Taglib.readMetadata(path = file.file.absolutePath)
                        ?: return@async null
                    ensureActive()
                    stateStore.updateLoading(
                        taskId = taskId,
                        message = metadata.title.orEmpty(),
                        progress = (index + 1).toFloat() / files.size.coerceAtLeast(1),
                    )
                    val path = file.absolutePath()
                    LAudio(
                        id = "${LAudio.ID_PREFIX}${path.md5()}",
                        title = metadata.title ?: "Unknown",
                        subtitle = metadata.artist ?: "Unknown",
                        mediaSourceName = name,
                        extra = metadata.toAudioExtra(mapOf("path" to path)),
                    )
                }
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val path = song.extra?.get("path") ?: return@withContext null
        Taglib.getLyric(path = path)
            ?: throw FileNotFoundException("Not found lyric for $path")
    }

    override suspend fun getPicture(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val path = song.extra?.get("path") ?: return@withContext null
        val picture = Taglib.getPicture(path = path)
            ?: throw FileNotFoundException("Not found picture for $path")
        MediaData.Bytes(picture)
    }

    override suspend fun getMedia(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        song.extra?.get("path")?.let(MediaData::Url)
    }
}
