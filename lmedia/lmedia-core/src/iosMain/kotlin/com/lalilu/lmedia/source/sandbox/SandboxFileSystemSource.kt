package com.lalilu.lmedia.source.sandbox

import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.common.flow.toUpdatableFlow
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.*
import com.lalilu.lmedia.source.*
import io.github.vinceglb.filekit.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single(binds = [MediaSource::class, MediaDataSource::class])
@OptIn(ExperimentalForeignApi::class)
object SandboxFileSystemSource : MediaSource, CoroutineScope, MediaDataSource {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    override val name: String = "SandboxFileSystemSource"
    private val musicFolder = FileKit.filesDir
    private val fileFlow = flowOf(musicFolder.takeIf { it.exists() })

    override val config: MediaSourceConfig = buildConfig(key = name) {
        function<Unit>(
            key = "Refresh",
            description = "Refresh the sandbox folder"
        ).onCall {
            sourceFlow.requireUpdate()
        }
    }

    private val sourceFlow = fileFlow.map { root ->
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
            val metadata = runCatching { Taglib.readMetadata(path = file.absolutePath()) }
                .getOrNull()
                ?: return@mapNotNull null
            file to metadata
        } ?: emptyList()
    }.map { pairs ->
        val songs = pairs.map { (file, metadata) ->
            buildAudio(id = buildMediaId(file, musicFolder)) {
                title(metadata.title)
                subtitle(metadata.artist)
                source(SourceItem.FileItem(file))
                metadata(metadata)
            }
        }

        songs.buildSnapshot()
    }.toUpdatableFlow()

    private val sourceStateFlow = sourceFlow
        .stateIn(this, SharingStarted.Lazily, Snapshot.Empty)

    override fun source(): Flow<Snapshot> = sourceStateFlow
    override val dataSource: MediaDataSource = this


    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val audio = sourceStateFlow.value.audios.firstOrNull { it.idValue() == song.id }

        val fileItem = audio?.sourceItem as? SourceItem.FileItem
            ?: throw IllegalArgumentException("Invalid id: ${song.idValue()}")

        val path = fileItem.file.path
        if (path.isBlank()) throw IllegalArgumentException("Invalid path: $path")

        Taglib.getLyric(path = path)
            ?: throw FileNotFoundException("Not found lyric for $path")
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val audio = sourceStateFlow.value.audios.firstOrNull { it.idValue() == song.id }

        val fileItem = audio?.sourceItem as? SourceItem.FileItem
            ?: throw IllegalArgumentException("Invalid id: ${song.idValue()}")

        val path = fileItem.file.path
        if (path.isBlank()) throw IllegalArgumentException("Invalid path: $path")

        val bytes = Taglib.getPicture(path = path)
            ?: throw FileNotFoundException("Not found picture for $path")

        return MediaData.Bytes(bytes)
    }

    override suspend fun getMedia(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val audio = sourceStateFlow.value.audios.firstOrNull { it.idValue() == song.id }

        val fileItem = audio?.sourceItem as? SourceItem.FileItem
            ?: throw IllegalArgumentException("Invalid id: ${song.idValue()}")

        val file = fileItem.file
        if (!file.exists()) {
            throw FileNotFoundException("File not found: ${file.nsUrl}")
        }

        MediaData.Bytes(file.readBytes())
    }

    /**
     * 构建媒体ID
     * @param file 媒体文件
     * @param baseDirectory 沙盒根目录
     */
    private fun buildMediaId(file: PlatformFile, baseDirectory: PlatformFile): String {
        // 应用的沙盒路径每次重新安装都会改变，替换成相对路径确保唯一性
        return file.absolutePath()
            .substringAfter(baseDirectory.absolutePath())
            .md5()
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