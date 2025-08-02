package com.lalilu.lmedia.source

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SourceItem
import com.lalilu.lmedia.rpc.RemotableMediaSource
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import io.github.vinceglb.filekit.*
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.buffered
import java.io.FileNotFoundException
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalSettingsApi::class, ExperimentalCoroutinesApi::class)
class JvmFileSystemSource(
    private val settings: ObservableSettings
) : MediaSource, RemotableMediaSource, CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()

    companion object {
        private const val KEY_PATH = "path"
    }

    override val name: String = "JvmFileSystemSource"

    private val fileFlow = settings.getStringOrNullFlow(KEY_PATH)
        .mapLatest { path ->
            path?.let { PlatformFile(it) }
                ?.takeIf { it.exists() }
        }

    private val sourceStateFlow = fileFlow.map { root ->
        root?.filterChildren { file ->
            if (file.isDirectory()) return@filterChildren false
            if (file.size() < 10) return@filterChildren false

            file.source().buffered().use {
                val low4 = it.readInt()
                val high4 = it.readInt()

                (low4 == 0x664C6143 && high4 == 0x00000022) || (low4 == 0x4F676753 && high4 == 0x00020000)
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
                    sourceItem = SourceItem.FileItem(file.file)
                )
            }
        )
    }.stateIn(this, SharingStarted.Lazily, Snapshot.Empty)

    override suspend fun requireName(): String = name

    override fun requirePictureFlow(id: String, type: String): Flow<ByteArray?> {
        return sourceStateFlow
            .mapLatest { snapshot -> snapshot.audios.firstOrNull { it.id == id } }
            .mapLatest { audio ->
                val fileItem = audio?.sourceItem as? SourceItem.FileItem
                    ?: throw IllegalArgumentException("Invalid id: $id")

                val path = fileItem.file.path
                if (path.isBlank()) throw IllegalArgumentException("Invalid path: $path")

                val stream = Taglib.getPicture(path = path)?.inputStream()
                    ?: throw FileNotFoundException("Not found picture for $path")

                stream.readBytes()
            }
    }

    override fun requireLyricFlow(id: String, type: String): Flow<String?> {
        return sourceStateFlow
            .mapLatest { snapshot -> snapshot.audios.firstOrNull { it.id == id } }
            .mapLatest { audio ->
                val fileItem = audio?.sourceItem as? SourceItem.FileItem
                    ?: throw IllegalArgumentException("Invalid id: $id")

                val path = fileItem.file.path
                if (path.isBlank()) throw IllegalArgumentException("Invalid path: $path")

                Taglib.getLyric(path = path)
                    ?: throw FileNotFoundException("Not found picture for $path")
            }
    }

    override fun requireMediaFlow(id: String, type: String): Flow<ByteArray?> {
        return sourceStateFlow
            .mapLatest { snapshot -> snapshot.audios.firstOrNull { it.id == id } }
            .mapLatest { audio ->
                val fileItem = audio?.sourceItem as? SourceItem.FileItem
                    ?: throw IllegalArgumentException("Invalid id: $id")

                val file = fileItem.file
                if (!file.exists()) {
                    throw FileNotFoundException("File not found: ${file.absolutePath}")
                }

                if (!file.canRead()) {
                    throw SecurityException("Cannot read file: ${file.absolutePath}")
                }

                file.readBytes()
            }
    }

    override fun source(): Flow<Snapshot> = sourceStateFlow

    @Composable
    override fun Content(modifier: Modifier) {
        val scope = rememberCoroutineScope()
        val path = fileFlow.collectAsState(null)
        val source by remember { source() }.collectAsState(
            initial = Snapshot.Empty,
            context = Dispatchers.IO
        )

        val launcher = rememberDirectoryPickerLauncher {
            scope.launch(Dispatchers.IO) {
                settings.putString(KEY_PATH, it?.absolutePath() ?: "")
            }
        }

        JvmFileSystemSourceContent(
            modifier = modifier,
            title = name,
            path = path.value?.name ?: "",
            itemsCount = source.audios.size,
            onSelectDirectory = { launcher.launch() }
        )
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