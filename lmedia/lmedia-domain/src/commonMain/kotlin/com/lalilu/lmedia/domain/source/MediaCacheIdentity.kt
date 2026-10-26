package com.lalilu.lmedia.domain.source

data class MediaCacheIdentity(
    val scope: String,
    val revision: String,
    val size: Long,
    val etag: String? = null,
    val lastModified: String? = null,
)
