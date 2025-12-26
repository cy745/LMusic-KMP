package com.lalilu.lmedia.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.lmedia.component.FileSystemScannerCard
import com.lalilu.lmedia.component.FileSystemScannerCardIntent
import com.lalilu.lmedia.component.FileSystemScannerCardState
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SnapshotState
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.launch
import pro.respawn.flowmvi.annotation.InternalFlowMVIAPI

@OptIn(InternalFlowMVIAPI::class)
@Composable
fun JvmFileSystemSource.JvmFileSystemSourceContent(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val state = source().collectAsStateWithLifecycle(initialValue = Snapshot.Loading)
    val launcher = rememberDirectoryPickerLauncher {
        if (it == null) {
            return@rememberDirectoryPickerLauncher
        }

        scope.launch {
            val path = it.bookmarkData().bytes.decodeToString()
            config.update { setter ->
                setter("file_path", path)
            }
        }
    }

    FileSystemScannerCard(
        modifier = modifier,
        state = state.value.toCardState { config },
        onIntent = { intent ->
            when (intent) {
                FileSystemScannerCardIntent.Select -> launcher.launch()
                FileSystemScannerCardIntent.Cancel -> {}
                FileSystemScannerCardIntent.ReScan -> {}
            }
        }
    )
}

@Composable
fun Snapshot.toCardState(
    config: () -> MediaSourceConfig
): FileSystemScannerCardState {
    return remember(this) {
        val path = config().get<String>("file_path").getOrElse { "" }

        when (val snapshotState = state) {
            is SnapshotState.Success -> FileSystemScannerCardState.Success(result = this, path = path)
            is SnapshotState.Empty -> FileSystemScannerCardState.Success(result = this, path = path)
            is SnapshotState.Error -> FileSystemScannerCardState.Error(
                error = IllegalArgumentException(snapshotState.message),
                path = path
            )

            is SnapshotState.Idle -> FileSystemScannerCardState.NotSelected
            is SnapshotState.LoadingDynamic -> FileSystemScannerCardState.Scanning(
                progress = snapshotState.progress,
                message = snapshotState.message,
                path = path
            )

            is SnapshotState.Loading -> FileSystemScannerCardState.Scanning(
                progress = { snapshotState.progress },
                message = { snapshotState.message },
                path = path
            )
        }
    }
}