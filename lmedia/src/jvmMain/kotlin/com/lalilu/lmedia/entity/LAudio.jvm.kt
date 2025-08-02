package com.lalilu.lmedia.entity

import java.io.File

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual sealed interface SourceItem {
    actual val key: String

    data class FileItem(val file: File) : SourceItem {
        override val key: String = "${this::class.qualifiedName}_${file.absolutePath}"
    }
}