package com.lalilu.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lalilu.LMusicTheme

@Composable
fun preview(
    data: List<Any> = emptyList(),
    isDarkMode: Boolean = false,
    theme: @Composable (Boolean, @Composable () -> Unit) -> Unit = { darkMode, block ->
        DefaultTheme(darkMode || isDarkMode, block)
    },
    background: @Composable (@Composable () -> Unit) -> Unit = { DefaultBackground(it) },
    content: @Composable PreviewScope.() -> Unit,
) = previewWithConfiguration(
    configuration = { dataContext.addAll(data) },
    isDarkMode = isDarkMode,
    theme = theme,
    background = background,
    content = content
)

@Composable
fun previewWithConfiguration(
    configuration: PreviewScope.() -> Unit = { },
    isDarkMode: Boolean = false,
    theme: @Composable (Boolean, @Composable () -> Unit) -> Unit = { darkMode, block ->
        DefaultTheme(darkMode || isDarkMode, block)
    },
    background: @Composable (@Composable () -> Unit) -> Unit = {
        DefaultBackground(it)
    },
    content: @Composable PreviewScope.() -> Unit,
) {
    val previewScope = remember { PreviewScope().apply(configuration) }
    theme(isDarkMode) { background { previewScope.content() } }
}

@Composable
private fun DefaultTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    LMusicTheme(isDarkTheme = darkMode) { content() }
}

@Composable
private fun DefaultBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        content()
    }
}