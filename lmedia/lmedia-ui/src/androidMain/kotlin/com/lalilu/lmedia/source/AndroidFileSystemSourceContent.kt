package com.lalilu.lmedia.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.lmedia.component.SourceCard
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.SnapshotState
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.launch

@Composable
fun MediaSource.AndroidFileSystemSourceContent(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val state = source().collectAsStateWithLifecycle(initialValue = Snapshot.Loading)
    val launcher = rememberDirectoryPickerLauncher {
        if (it == null) {
            return@rememberDirectoryPickerLauncher
        }

        scope.launch {
            val path = it.bookmarkData().bytes.decodeToString()
            config.update { setter -> setter("file_path", path) }
            config.call<Unit>("Rescan")
        }
    }

    val extraFunctions = remember {
        listOf<Declaration.Function<*>>(
            Declaration.Function(
                key = "Select Directory",
                name = "Select Directory",
                description = "Select Directory",
                parameters = emptyList(),
                returnType = Unit::class,
                isAvailable = { state.value.state is SnapshotState.Idle },
                callback = { launcher.launch() }
            )
        )
    }

    SourceCard(
        modifier = modifier,
        state = { state.value },
        extraFunctions = { extraFunctions },
        extraMessage = msg@{
            if (state.value.state is SnapshotState.Idle) return@msg null
            config.get<String>("file_path").getOrNull()
        }
    )
}