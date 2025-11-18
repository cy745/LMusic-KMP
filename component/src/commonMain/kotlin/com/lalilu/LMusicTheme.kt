package com.lalilu

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialTheme

@Composable
fun LMusicTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    DynamicMaterialTheme(
        seedColor = Color.Red,
        isDark = isDarkTheme,
        animate = true,
        content = content,
    )
}