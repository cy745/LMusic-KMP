package com.lalilu.lmedia.source

import com.lalilu.common.ext.io
import com.lalilu.common.kv.KVContext
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SourceItem
import com.lalilu.lmedia.entity.buildSnapshot
import com.russhwolf.settings.ExperimentalSettingsApi
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.buffered
import java.io.FileNotFoundException
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalSettingsApi::class, ExperimentalCoroutinesApi::class)
class JvmFileSystemSource(
    kv: KVContext
) : MediaSource, MediaDataSource, CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()

    companion object {
        const val KEY_PATH = "path"
    }

    override val name: String = "JvmFileSystemSource"

    val pathKV = kv.obtain<String>(KEY_PATH)
    val fileFlow = pathKV.flow().mapLatest { path ->
        PlatformFile.fromBookmarkData(path.encodeToByteArray())
            .takeIf { it.exists() }
    }

    private val sourceStateFlow = fileFlow.map { root ->
        root?.filterChildren { file ->
            if (file.isDirectory()) return@filterChildren false
            if (file.size() < 10) return@filterChildren false

            MagicNumber.match(
                ext = file.extension,
                source = file.source().buffered()
            ) != null
        }
    }.map { files ->
        files?.mapNotNull { file ->
            val metadata = Taglib.readMetadata(path = file.absolutePath()) ?: return@mapNotNull null
            file to metadata
        } ?: emptyList()
    }.map { songs ->
        val songs = songs.map { (file, metadata) ->
            LAudio(
                id = file.absolutePath(),
                title = metadata.title,
                subtitle = metadata.artist,
                sourceItem = SourceItem.FileItem(file.file),
                mediaSourceName = this@JvmFileSystemSource.name,
                metadata = metadata
            )
        }
        songs.buildSnapshot()
    }.stateIn(this, SharingStarted.Lazily, Snapshot.Loading)

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val audio = sourceStateFlow.value.audios.firstOrNull { it.id == song.id }

        val fileItem = audio?.sourceItem as? SourceItem.FileItem
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")

        val path = fileItem.file.path
        if (path.isBlank()) throw IllegalArgumentException("Invalid path: $path")

        Taglib.getLyric(path = path)
            ?: throw FileNotFoundException("Not found lyric for $path")
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val audio = sourceStateFlow.value.audios.firstOrNull { it.id == song.id }

        val fileItem = audio?.sourceItem as? SourceItem.FileItem
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")

        val path = fileItem.file.path
        if (path.isBlank()) throw IllegalArgumentException("Invalid path: $path")

        val bytes = Taglib.getPicture(path = path)
            ?: throw FileNotFoundException("Not found picture for $path")

        return MediaData.Bytes(bytes)
    }

    override suspend fun getMedia(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val audio = sourceStateFlow.value.audios.firstOrNull { it.id == song.id }

        val fileItem = audio?.sourceItem as? SourceItem.FileItem
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")

        val file = fileItem.file
        if (!file.exists()) {
            throw FileNotFoundException("File not found: ${file.absolutePath}")
        }

        if (!file.canRead()) {
            throw SecurityException("Cannot read file: ${file.absolutePath}")
        }

        MediaData.Bytes(file.readBytes())
    }

    override val dataSource: MediaDataSource = this
    override fun source(): Flow<Snapshot> = sourceStateFlow
}

private fun PlatformFile.filterChildren(block: (file: PlatformFile) -> Boolean): Collection<PlatformFile> {
    // 若不是文件夹，则无法遍历
    if (!this.isDirectory()) {
        // 若根元素即满足要求，且其不是文件夹，则直接返回根元素，否则直接返回空数组
        return if (block(this)) listOf(this) else emptyList()
    }

    val directory = mutableSetOf<PlatformFile>(this)
    val result = mutableSetOf<PlatformFile>()

    while (directory.isNotEmpty()) {
        val children = directory.map { it.list() }
            .flatten()

        directory.clear()
        children.forEach {
            if (it.isDirectory()) directory.add(it)
            if (block(it)) result.add(it)
        }
    }

    return result
}