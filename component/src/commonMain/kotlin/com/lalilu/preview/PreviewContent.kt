package com.lalilu.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun preview(
    data: List<Any> = emptyList(),
    theme: @Composable (@Composable () -> Unit) -> Unit = { DefaultTheme(it) },
    background: @Composable (@Composable () -> Unit) -> Unit = { DefaultBackground(it) },
    content: @Composable PreviewScope.() -> Unit,
) = preview(
    configuration = { dataContext.addAll(data) },
    theme = theme,
    background = background,
    content = content
)

@Composable
fun preview(
    configuration: PreviewScope.() -> Unit = { },
    theme: @Composable (@Composable () -> Unit) -> Unit = { DefaultTheme(it) },
    background: @Composable (@Composable () -> Unit) -> Unit = { DefaultBackground(it) },
    content: @Composable PreviewScope.() -> Unit,
) {
    val previewScope = remember { PreviewScope().apply(configuration) }
    theme { background { previewScope.content() } }
}

@Composable
private fun DefaultTheme(content: @Composable () -> Unit) {
    MaterialTheme { content() }
}

@Composable
private fun DefaultBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        content()
    }
}