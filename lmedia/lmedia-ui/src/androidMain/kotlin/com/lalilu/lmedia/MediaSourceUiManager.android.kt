package com.lalilu.lmedia

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.lmedia.source.AndroidFileSystemSourceContent
import com.lalilu.lmedia.source.MediaSource
import com.lalilu.lmedia.source.filesystem.AndroidFileSystemSource

@Composable
actual fun MediaSource.platformMediaSourceContent(modifier: Modifier): Boolean {
    when (this) {
        is AndroidFileSystemSource -> {
            AndroidFileSystemSourceContent(modifier)
            return true
        }
    }

    return false
}