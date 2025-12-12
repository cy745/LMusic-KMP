package com.lalilu.lmedia.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.lalilu.common.ext.io
import com.lalilu.lmedia.component.FileSystemScannerCard
import com.lalilu.lmedia.component.FileSystemScannerCardIntent
import com.lalilu.lmedia.component.FileSystemScannerCardState
import com.lalilu.lmedia.source.filesystem.AndroidFileSystemSource
import com.lalilu.lmedia.source.filesystem.AndroidFileSystemSourceIntent
import com.lalilu.lmedia.source.filesystem.AndroidFileSystemSourceState
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pro.respawn.flowmvi.annotation.InternalFlowMVIAPI

private fun AndroidFileSystemSourceState.toCardState(): FileSystemScannerCardState {
    return when (this) {
        AndroidFileSystemSourceState.NotSelected -> FileSystemScannerCardState.NotSelected

        is AndroidFileSystemSourceState.Success -> FileSystemScannerCardState.Success(
            result = result,
            path = path
        )

        is AndroidFileSystemSourceState.Error -> FileSystemScannerCardState.Error(
            error = error,
            path = path
        )

        is AndroidFileSystemSourceState.Scanning -> FileSystemScannerCardState.Scanning(
            progress = progress,
            message = message,
            path = path
        )

        else -> FileSystemScannerCardState.Error(
            error = IllegalArgumentException("Unknown state"),
            path = "Empty path"
        )
    }
}

@OptIn(InternalFlowMVIAPI::class)
@Composable
fun AndroidFileSystemSource.AndroidFileSystemSourceContent(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val state = store.states.collectAsState()
    val launcher = rememberDirectoryPickerLauncher {
        if (it == null) {
            return@rememberDirectoryPickerLauncher
        }

        scope.launch(Dispatchers.io) {
            val path = it.bookmarkData().bytes.decodeToString()
            store.intent(AndroidFileSystemSourceIntent.SelectFile(path))
        }
    }

    FileSystemScannerCard(
        modifier = modifier,
        state = state.value.toCardState(),
        onIntent = { intent ->
            when (intent) {
                FileSystemScannerCardIntent.Select -> launcher.launch()
                FileSystemScannerCardIntent.Cancel -> store.intent(AndroidFileSystemSourceIntent.CancelScanning)
                FileSystemScannerCardIntent.ReScan -> store.intent(AndroidFileSystemSourceIntent.ReStartScanning)
            }
        }
    )
}