package com.lalilu.lmedia.source.mediastore

import kotlinx.serialization.Serializable

@Serializable
data class MediaStoreSourceConfig(
    val minDurationSeconds: Int = 10,
)
