package com.lalilu.lmedia

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path

actual fun platformCacheDirectory(): String? = FileKit.cacheDir.path
