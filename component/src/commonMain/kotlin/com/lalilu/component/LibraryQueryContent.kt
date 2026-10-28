package com.lalilu.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.extensions.LibraryQueryState
import kotlinx.coroutines.delay

internal data class LibraryQueryPresentation(
    val loading: Boolean,
    val hasResult: Boolean,
    val failed: Boolean,
    val retry: () -> Unit,
    val searching: Boolean,
)
internal val LocalLibraryQuery = staticCompositionLocalOf<LibraryQueryPresentation?> { null }

@Composable
fun LibraryQueryContent(state: LibraryQueryState<*>, searching: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLibraryQuery provides LibraryQueryPresentation(
        state.loading, state.hasResult, state.error != null, state::retry, searching,
    ), content = content)
}

/** Delay only the waiting label, never successful results or the decision that a query is empty. */
@Composable
fun LibraryLoadingState(
    modifier: Modifier = Modifier,
    searching: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 32.dp),
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(searching) { visible = false; delay(300); visible = true }
    Box(modifier.fillMaxWidth().padding(contentPadding).heightIn(min = 18.dp)) {
        if (visible) Text(if (searching) "正在搜索…" else "正在读取曲库…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
    }
}

@Composable
fun libraryQueryHasResult(): Boolean = LocalLibraryQuery.current?.hasResult != false
