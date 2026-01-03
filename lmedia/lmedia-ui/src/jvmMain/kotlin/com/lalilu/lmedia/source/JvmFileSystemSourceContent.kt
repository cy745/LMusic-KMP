package com.lalilu.lmedia.source

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.lmedia.component.SourceCard
import com.lalilu.lmedia.component.SourceState
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
            config.update { setter -> setter("file_path", path) }
            config.call<Unit>("Reload")
        }
    }

    SourceCard(
        modifier = modifier,
        state = state.value.toCardState { config },
        sourceActions = {
            TextButton(
                onClick = { launcher.launch() },
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text(
                    text = "Select",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    )
}

@Composable
fun Snapshot.toCardState(
    config: () -> MediaSourceConfig
): SourceState {
    return remember(this) {
        val path = config().get<String>("file_path").getOrElse { "" }

        when (val snapshotState = state) {
            is SnapshotState.Success -> SourceState.Success(result = this, state = path)
            is SnapshotState.Empty -> SourceState.Success(result = this, state = path)
            is SnapshotState.Error -> SourceState.Error(
                error = IllegalArgumentException(snapshotState.message),
                state = path
            )

            is SnapshotState.Idle -> SourceState.Idle
            is SnapshotState.LoadingDynamic -> SourceState.Loading(
                progress = snapshotState.progress,
                message = snapshotState.message,
                state = path
            )

            is SnapshotState.Loading -> SourceState.Loading(
                progress = { snapshotState.progress },
                message = { snapshotState.message },
                state = path
            )
        }
    }
}