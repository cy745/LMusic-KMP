package com.lalilu.lmedia.source.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicResponseWrapper<T : SubsonicResponse>(
    @SerialName("subsonic-response")
    val response: T
)