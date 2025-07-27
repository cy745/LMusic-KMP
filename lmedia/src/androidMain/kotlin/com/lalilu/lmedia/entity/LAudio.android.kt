package com.lalilu.lmedia.entity

import android.net.Uri
import java.io.File

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual sealed interface SourceItem {
    data class UriItem(val uri: Uri) : SourceItem
    data class FileItem(val file: File) : SourceItem
    data class FilePathItem(val path: String) : SourceItem
}