package com.lalilu.lmedia.entity

import android.net.Uri
import java.io.File

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual sealed interface SourceItem {
    actual val key: String

    data class UriItem(val uri: Uri) : SourceItem {
        override val key: String = "${this::class::qualifiedName}_$uri"
    }

    data class FileItem(val file: File) : SourceItem {
        override val key: String = "${this::class::qualifiedName}_${file.absolutePath}"
    }

    data class FilePathItem(val path: String) : SourceItem {
        override val key: String = "${this::class::qualifiedName}_$path"
    }
}