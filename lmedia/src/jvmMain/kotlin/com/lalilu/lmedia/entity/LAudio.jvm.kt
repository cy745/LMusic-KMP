package com.lalilu.lmedia.entity

import java.io.File

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual sealed interface SourceItem {
    data class FileItem(val file: File) : SourceItem
}