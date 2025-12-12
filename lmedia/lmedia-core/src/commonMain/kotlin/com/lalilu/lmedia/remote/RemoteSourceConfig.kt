package com.lalilu.lmedia.remote

import kotlinx.serialization.Serializable

@Serializable
data class RemoteSourceConfig(
    val enable: Boolean = false,
    val url: String = "",
    val password: String = ""
) {
    companion object {
        val Empty = RemoteSourceConfig()
    }
}