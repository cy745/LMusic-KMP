package com.lalilu.lmedia.source

import com.blankj.utilcode.util.Utils
import com.lalilu.lmedia.source.mediastore.MediaStoreSource

actual val PlatformMediaSource: List<MediaSource> by lazy {
    listOf(
        MediaStoreSource(Utils.getApp()),
        AndroidFileSystemSource
    )
}
