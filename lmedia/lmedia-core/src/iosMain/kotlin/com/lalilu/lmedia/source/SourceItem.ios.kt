package com.lalilu.lmedia.source

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
@OptIn(ExperimentalForeignApi::class)
actual sealed interface SourceItem {
    actual val key: String

    data class MusicKitItem(val item: com.lalilu.lmedia.SongInfo) : SourceItem {
        @OptIn(ExperimentalForeignApi::class)
        override val key: String = "MusicKitItem|${item.title()}"
    }

    data class FilePathItem(val path: String) : SourceItem {
        override val key: String = "FilePathItem|$path"
    }
}
