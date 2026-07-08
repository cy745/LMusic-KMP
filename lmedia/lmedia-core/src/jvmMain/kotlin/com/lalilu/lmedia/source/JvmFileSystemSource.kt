package com.lalilu.lmedia.source

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LFolder
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.domain.source.buildSnapshot
import com.lalilu.lmedia.source.buildConfig
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import org.koin.core.annotation.Single
import java.io.FileNotFoundException


@Single(binds = [MediaSource::class, MediaDataSource::class])
class JvmFileSystemSource(
    private val saver: com.lalilu.lmedia.source.Saver? = null
) : MediaSource, MediaDataSource {
    override val name: String = "JvmFileSystemSource"
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _snapshot = MutableStateFlow(Snapshot.Loading)

    override fun source(): Flow<Snapshot> = _snapshot

    override val config: MediaSourceConfig = buildConfig(key = name) {
        function<Unit>(
            key = "Refresh",
            description = "Refresh the local file system"
        ).onCall {
            scope.launch { refresh() }
        }
    }

    init {
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        _snapshot.value = Snapshot.Loading
        try {
            val root = FileKit.filesDir.takeIf { it.exists() } ?: run {
                _snapshot.value = Snapshot.Empty
                return
            }
            val audioFiles = mutableListOf<LAudio>()
            scope.launch {
                root.walkFiles().forEach { file ->
                    if (MagicNumber.match(
                            ext = file.extension,
                            source = file.source().buffered()
                        ) != null
                    ) {
                        Taglib.readMetadata(path = file.absolutePath())?.let { metadata ->
                            audioFiles.add(
                                LAudio(
                                    id = "${LAudio.ID_PREFIX}${file.absolutePath().md5()}",
                                    title = metadata.title ?: file.name,
                                    subtitle = metadata.artist ?: "Unknown",
                                    mediaSourceName = name,
                                    metadata = Metadata(
                                        title = metadata.title, album = metadata.album,
                                        artist = metadata.artist, duration = metadata.duration
                                    )
                                )
                            )
                        }
                    }
                }
            }.join()

            _snapshot.value = buildSnapshot(audioFiles)
        } catch (e: Exception) {
            Logger.e("JvmFileSystemSource", e)
            _snapshot.value = Snapshot(state = SnapshotState.Error(e.message ?: "Error"))
        }
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        return try {
            val file = song.extra?.get("path")?.let { java.io.File(it) }
                ?: throw FileNotFoundException("No path for ${song.id}")
            if (!file.exists()) throw FileNotFoundException("File not found: ${file.absolutePath}")
            MediaData.Bytes(file.readBytes())
        } catch (e: Exception) {
            Logger.e("JvmFileSystemSource", e)
            null
        }
    }
}

private fun PlatformFile.walkFiles(): Sequence<PlatformFile> = sequence {
    if (isDirectory()) {
        list().forEach { child ->
            yieldAll(child.walkFiles())
        }
    } else {
        yield(this@walkFiles)
    }
}

private fun String.md5(): String = java.security.MessageDigest
    .getInstance("MD5")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(it) }
