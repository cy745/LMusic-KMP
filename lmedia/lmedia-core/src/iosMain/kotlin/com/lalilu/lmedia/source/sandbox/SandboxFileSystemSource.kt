package com.lalilu.lmedia.source.sandbox

import com.lalilu.common.flow.toUpdatableFlow
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata as DomainMetadata
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource as DomainMediaSource
import com.lalilu.lmedia.source.MediaSourceConfig
import com.lalilu.lmedia.source.buildConfig
import com.lalilu.lmedia.source.Configurable
import kotlinx.cinterop.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.koin.core.annotation.Single
import platform.Foundation.*
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5

@Single(binds = [com.lalilu.lmedia.domain.source.MediaSource::class, MediaDataSource::class])
@OptIn(ExperimentalForeignApi::class)
class SandboxFileSystemSource : DomainMediaSource, MediaDataSource, Configurable {
    override val name: String = "SandboxFileSystemSource"

    /** iOS Documents directory (for iTunes file sharing). */
    private val documentsPath: String? =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String)

    private val audioFileMap = mutableMapOf<String, String>() // audioId → filePath

    override val config: MediaSourceConfig =
        buildConfig(key = name) {
            function<Unit>(
                key = "Refresh",
                description = "Refresh the sandbox folder"
            ).onCall {
                sourceFlow.requireUpdate()
            }
        }

    private val sourceFlow = flowOf(documentsPath)
        .map { dir ->
            audioFileMap.clear()
            dir?.let { scanDirectory(it) } ?: emptyList()
        }
        .map { paths ->
            paths.mapNotNull { path ->
                val metadata = runCatching {
                    kotlinx.coroutines.runBlocking(Dispatchers.io) {
                        Taglib.readMetadata(path = path)
                    }
                }.getOrNull() ?: return@mapNotNull null

                val audioId = "${LAudio.ID_PREFIX}${path.md5()}"
                audioFileMap[audioId] = path
                LAudio(
                    id = audioId,
                    title = metadata.title ?: "Unknown",
                    subtitle = metadata.artist ?: "Unknown Subs",
                    mediaSourceName = name,
                    metadata = DomainMetadata(
                        title = metadata.title, album = metadata.album,
                        artist = metadata.artist, albumArtist = metadata.albumArtist,
                        composer = metadata.composer, lyricist = metadata.lyricist,
                        comment = metadata.comment, genre = metadata.genre,
                        track = metadata.track, disc = metadata.disc, date = metadata.date,
                        duration = metadata.duration,
                        dateAdded = metadata.dateAdded, dateModified = metadata.dateModified
                    )
                )
            }
        }
        .map { songs -> com.lalilu.lmedia.domain.source.buildSnapshot(songs) }
        .toUpdatableFlow()

    override fun source(): Flow<com.lalilu.lmedia.domain.source.Snapshot> = sourceFlow

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val path = audioFileMap[song.id]
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")
        Taglib.getLyric(path = path)
            ?: throw FileNotFoundException("Not found lyric for $path")
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val path = audioFileMap[song.id]
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")
        val bytes = Taglib.getPicture(path = path)
            ?: throw FileNotFoundException("Not found picture for $path")
        return MediaData.Bytes(bytes)
    }

    override suspend fun getMedia(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val path = audioFileMap[song.id]
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")
        val nsData = NSData.dataWithContentsOfFile(path)
            ?: throw FileNotFoundException("File not found: $path")
        val length = nsData.length.toInt()
        val bytes = if (length > 0) {
            val rawPtr = nsData.bytes
                ?: throw Exception("Null bytes pointer for $path")
            rawPtr.readBytes(length)
        } else ByteArray(0)
        MediaData.Bytes(bytes)
    }

    private fun scanDirectory(
        dirPath: String,
        result: MutableList<String> = mutableListOf()
    ): List<String> {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(dirPath, null) as? List<String> ?: return result

        for (file in contents) {
            if (file == "." || file == "..") continue
            val fullPath = "$dirPath/$file"
            if (!fm.fileExistsAtPath(fullPath)) continue

            val attrs = fm.attributesOfItemAtPath(fullPath, null)
            val isDirectory = (attrs?.get(NSFileType) as? String) == NSFileTypeDirectory

            if (isDirectory) {
                scanDirectory(fullPath, result)
            } else {
                val ext = file.substringAfterLast('.')
                // Check magic number by reading file header bytes
                val isAudio = runCatching {
                    val path = Path(fullPath)
                    val source = SystemFileSystem.source(Path(fullPath))
                    source.buffered().use { buffered ->
                        MagicNumber.match(ext = ext, source = buffered) != null
                    }
                }.getOrDefault(false)

                if (isAudio) {
                    result.add(fullPath)
                }
            }
        }
        return result
    }
}
