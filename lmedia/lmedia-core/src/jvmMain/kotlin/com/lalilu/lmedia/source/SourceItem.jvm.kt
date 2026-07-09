package com.lalilu.lmedia.source

import java.io.File

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual sealed interface SourceItem {
    actual val key: String

    data class FileItem(val file: File) : SourceItem {
        override val key: String = "FileItem|${file.absolutePath}"
    }

    data class FilePathItem(val path: String) : SourceItem {
        override val key: String = "FilePathItem|$path"
    }
}
