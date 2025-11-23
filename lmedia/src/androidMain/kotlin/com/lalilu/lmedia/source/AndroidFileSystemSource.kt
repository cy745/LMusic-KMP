package com.lalilu.lmedia.source

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.MagicNumber
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SourceItem
import com.lalilu.lmedia.entity.buildSnapshot
import io.github.vinceglb.filekit.*
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.buffered
import java.io.FileNotFoundException
import kotlin.coroutines.CoroutineContext

@SuppressLint("NewApi")
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidFileSystemSource(
    private val context: Application,
    private val lMediaKV: LMediaKV
) : MediaSource, MediaDataSource, CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io + SupervisorJob()
    override val name: String = "AndroidFileSystemSource"

    private val filePathState = lMediaKV.obtain<String>("file_path")
    private val selectedFile = filePathState.flow()
        .mapLatest { path ->
            PlatformFile(path)
                .takeIf { it.exists() }
        }

    private val sourceStateFlow = selectedFile.map { root ->
        root?.filterChildren { file ->
            if (file.isDirectory()) return@filterChildren false
            if (file.size() < 10) return@filterChildren false

            MagicNumber.match(
                ext = file.extension,
                source = file.source().buffered()
            ) != null
        }
    }.map { files ->
        files?.map { it.androidFile }?.mapNotNull { file ->
            when (file) {
                is AndroidFile.FileWrapper -> {
                    val metadata = Taglib.readMetadata(path = file.file.absolutePath)
                        ?: return@mapNotNull null

                    SourceItem.FileItem(file.file) to metadata
                }

                is AndroidFile.UriWrapper -> {
                    val metadata = context.contentResolver
                        .openFileDescriptor(file.uri, "r")
                        ?.use { Taglib.readMetadata(fd = it.detachFd()) }
                        ?: return@mapNotNull null

                    SourceItem.UriItem(file.uri) to metadata
                }
            }
        } ?: emptyList()
    }.map { result ->
        val songs = result.map { (source, metadata) ->
            LAudio(
                id = source.key,
                title = metadata.title,
                subtitle = metadata.artist,
                sourceItem = source,
                metadata = metadata,
                mediaSourceName = this@AndroidFileSystemSource.name
            )
        }

        songs.buildSnapshot()
    }.stateIn(this, SharingStarted.Lazily, Snapshot.Empty)

    override fun source(): Flow<Snapshot> = sourceStateFlow
    override val dataSource: MediaDataSource = this

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val audio = sourceStateFlow.value.audios.firstOrNull { it.id == song.id }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")

        when (sourceItem) {
            is SourceItem.FileItem -> {
                val file = sourceItem.file

                val lyric = Taglib.getLyric(path = file.absolutePath)
                    ?: throw FileNotFoundException("Not found lyric for $file")

                lyric
            }

            is SourceItem.FilePathItem -> {
                val uri = sourceItem.path.toUri()

                val lyric = context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { Taglib.getLyric(fd = it.detachFd()) }
                    ?: throw FileNotFoundException("Not found lyric for $uri")

                lyric
            }

            else -> null
        }
    }

    override suspend fun getPicture(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val audio = sourceStateFlow.value.audios.firstOrNull { it.id == song.id }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")

        when (sourceItem) {
            is SourceItem.FileItem -> {
                val file = sourceItem.file

                val picture = Taglib.getPicture(path = file.absolutePath)
                    ?: throw FileNotFoundException("Not found picture for $file")

                MediaData.Bytes(picture)
            }

            is SourceItem.FilePathItem -> {
                val uri = sourceItem.path.toUri()

                val picture = context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { Taglib.getPicture(fd = it.detachFd()) }
                    ?: throw FileNotFoundException("Not found picture for $uri")

                MediaData.Bytes(picture)
            }

            is SourceItem.UriItem -> {
                val uri = sourceItem.uri

                val picture = context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { Taglib.getPicture(fd = it.detachFd()) }
                    ?: throw FileNotFoundException("Not found picture for $uri")

                MediaData.Bytes(picture)
            }

            else -> null
        }
    }

    override suspend fun getMedia(song: LAudio): MediaData? = withContext(Dispatchers.io) {
        val audio = sourceStateFlow.value.audios.firstOrNull { it.id == song.id }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.id}")

        when (sourceItem) {
            is SourceItem.FileItem -> {
                val file = sourceItem.file
                MediaData.Bytes(file.readBytes())
            }

            is SourceItem.FilePathItem -> {
                MediaData.Url(sourceItem.path)
            }

            else -> null
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val launcher = rememberDirectoryPickerLauncher {
            filePathState.value = it?.absolutePath() ?: ""
        }
        val source by remember { source() }
            .collectAsState(Snapshot.Empty)

        Card(modifier = modifier) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = name)

                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    source.audios.forEach {
                        Text(text = "${it.title} - ${it.subtitle}")
                    }
                }

                Button(onClick = { launcher.launch() }) {
                    Text(text = "Select Directory")
                }
            }
        }
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