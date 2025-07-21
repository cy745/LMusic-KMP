package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import io.github.vinceglb.filekit.*
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.io.buffered

object JvmFileSystemSource : MediaSource {
    override val name: String = "JvmFileSystemSource"
    private val selectedFile = MutableStateFlow<PlatformFile?>(null)

    override fun source(): Flow<Snapshot> {
        val filesFlow = selectedFile.map { root ->
            root?.filterChildren { file ->
                if (file.isDirectory()) return@filterChildren false
                if (file.size() < 10) return@filterChildren false

                file.source().buffered().use {
                    val low4 = it.readInt()
                    val high4 = it.readInt()

                    println("[${file.absolutePath()}]: ${low4.toHexString()} ${high4.toHexString()}")
                    (low4 == 0x664C6143 && high4 == 0x00000022) || (low4 == 0x4F676753 && high4 == 0x00020000)
                }
            }
        }

        return filesFlow.map { files ->
            Snapshot(
                audios = files?.map { LAudio(title = it.name) }
                    ?: emptyList()
            )
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val scope = rememberCoroutineScope()
        val path = selectedFile.collectAsState()
        val launcher = rememberDirectoryPickerLauncher {
            scope.launch(Dispatchers.IO) { selectedFile.emit(it) }
        }
        val source by remember { source() }
            .collectAsState(Snapshot.Empty)

        Card(modifier = modifier) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = name)

                if (path.value != null) {
                    Text(text = "${path.value?.name}")
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    source.audios.forEach {
                        Text(text = it.title)
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