package com.lalilu.lmedia

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.source.SandBoxFileSystemSourceContent
import com.lalilu.lmedia.source.sandbox.SandboxFileSystemSource

@Composable
actual fun MediaSource.platformMediaSourceContent(modifier: Modifier): Boolean {
    when (this) {
        is SandboxFileSystemSource -> {
            SandBoxFileSystemSourceContent(modifier)
            return true
        }
    }
    return false
}