package com.lalilu.lmedia.domain.source

import kotlinx.serialization.Serializable

@Serializable
sealed class MediaData {
    @Serializable
    data class Url(val url: String) : MediaData()

    @Serializable
    class Bytes(val bytes: ByteArray) : MediaData()
}
