package com.lalilu.lmedia.source

import com.lalilu.common.ext.io
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SourceItem
import io.github.vinceglb.filekit.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalForeignApi::class)
object SandboxFileSystemSource : MediaSource, CoroutineScope, MediaDataSource {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    override val name: String = "SandboxFileSystemSource"
    private val musicFolder = FileKit.filesDir
    private val fileFlow = flowOf(musicFolder.takeIf { it.exists() })

    private val sourceStateFlow = fileFlow.map { root ->
        root?.filterChildren { file ->
            if (file.isDirectory()) return@filterChildren false
            if (file.size() < 10) return@filterChildren false

            file.source().buffered().use {
                val low4 = it.readInt()
                val high4 = it.readInt()

                (low4 == 0x664C6143 && high4 == 0x00000022) // flac
                        || (low4 == 0x4F676753 && high4 == 0x00020000) // ogg
                        || (low4 and 0x49443300 != 0) // mp3
            }
        }
    }.map { files ->
        files?.mapNotNull { file ->
            val metadata = Taglib.readMetadata(path = file.absolutePath()) ?: return@mapNotNull null
            file to metadata
        } ?: emptyList()
    }.map { songs ->
        Snapshot(
            audios = songs.map { (file, metadata) ->
                LAudio(
                    id = file.absolutePath(),
                    title = metadata.title,
                    subtitle = metadata.artist,
                    sourceItem = SourceItem.FileItem(file),
                    mediaSourceName = this@SandboxFileSystemSource.name
                )
            }
        )
    }.stateIn(this, SharingStarted.Lazily, Snapshot.Empty)


    override fun source(): Flow<Snapshot> = sourceStateFlow
    override val dataSource: MediaDataSource = this


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
            throw FileNotFoundException("File not found: ${file.nsUrl}")
        }

        MediaData.Bytes(file.readBytes())
    }
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