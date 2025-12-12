package com.lalilu.lmedia

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.lmedia.source.MediaSource

@Composable
actual fun MediaSource.platformMediaSourceContent(modifier: Modifier): Boolean {
    return false
}