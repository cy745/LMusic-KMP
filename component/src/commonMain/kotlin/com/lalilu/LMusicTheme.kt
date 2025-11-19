package com.lalilu

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialTheme

val LocalSeedColor = staticCompositionLocalOf<MutableState<Color>> { error("No seed color state provided") }

@Composable
fun LMusicTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val seedColorState = remember { mutableStateOf(Color.Red) }

    CompositionLocalProvider(LocalSeedColor provides seedColorState) {
        DynamicMaterialTheme(
            seedColor = seedColorState.value,
            isDark = isDarkTheme,
            animate = true,
            content = content,
        )
    }
}