package com.lalilu.lmedia.server.entity

import kotlinx.serialization.Serializable

@Serializable
data class RemoteServerConfig(
    val enable: Boolean = false,
    val port: Int = 7779,
    val password: String = "",
    val sourceName: String = ""
) {
    companion object Companion {
        val Empty = RemoteServerConfig()
    }
}