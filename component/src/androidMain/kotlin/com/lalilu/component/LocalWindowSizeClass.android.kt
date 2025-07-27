package com.lalilu.component

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
actual fun ProvideWindowSizeClass(content: @Composable (() -> Unit)) {
    val context = LocalContext.current

    CompositionLocalProvider(
        value = LocalWindowSizeClass provides calculateWindowSizeClass(context as android.app.Activity),
        content = content
    )
}