package com.lalilu.lmedia.source

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourcePipelineCard
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.filesystem.AndroidFileSystemSource
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

fun AndroidFileSystemSource.androidFileSystemSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val scope = rememberCoroutineScope()
    val repository = koinInject<MediaSourceBindingRepository>()
    val syncState = state.collectAsStateWithLifecycle()
    val latestSnapshot = snapshot.collectAsStateWithLifecycle()
    val status = repository.observeSource(name)
        .collectAsStateWithLifecycle(initialValue = null)
    val launcher = rememberDirectoryPickerLauncher {
        if (it == null) {
            return@rememberDirectoryPickerLauncher
        }

        scope.launch {
            val path = it.bookmarkData().bytes.decodeToString()
            configOrNullCompat?.update { setter -> setter("file_path", path) }
            configOrNullCompat?.call<Unit>("Rescan")
        }
    }

    val extraFunctions = remember {
        listOf<Declaration.Function<*>>(
            Declaration.Function(
                key = "Select",
                name = "Select",
                description = "选择扫描目录",
                parameters = emptyList(),
                returnType = Unit::class,
                isAvailable = { syncState.value !is SnapshotState.Loading },
                callback = { launcher.launch() }
            )
        )
    }

    return@LazyStaggeredGridContent {
        item(key = this@androidFileSystemSourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                status = {
                    status.value ?: SourceStatus(
                        syncState = syncState.value,
                        resultRevision = latestSnapshot.value?.revision,
                        songCount = latestSnapshot.value?.audios?.size ?: 0,
                    )
                },
                snapshot = { latestSnapshot.value },
                extraFunctions = { extraFunctions },
                extraMessage = msg@{
                    if (syncState.value is SnapshotState.Idle) return@msg null
                    configOrNullCompat?.get<String>("file_path")?.getOrNull()
                }
            )
        }
    }
}
