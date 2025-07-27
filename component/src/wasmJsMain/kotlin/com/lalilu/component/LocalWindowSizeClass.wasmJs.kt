package com.lalilu.component

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
actual fun ProvideWindowSizeClass(content: @Composable (() -> Unit)) {
    CompositionLocalProvider(
        value = LocalWindowSizeClass provides calculateWindowSizeClass(),
        content = content
    )
}