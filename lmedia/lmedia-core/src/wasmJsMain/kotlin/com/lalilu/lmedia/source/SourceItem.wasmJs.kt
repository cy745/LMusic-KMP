package com.lalilu.lmedia.source

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual sealed interface SourceItem {
    actual val key: String

    data class FilePathItem(val path: String) : SourceItem {
        override val key: String = "FilePathItem|$path"
    }
}
