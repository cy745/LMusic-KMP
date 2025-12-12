package com.lalilu.lmedia.rpc

import kotlinx.serialization.Serializable

@Serializable
data class RemoteServerConfig(
    val enable: Boolean = false,
    val port: Int = 8087,
    val password: String = "",
    val selectedSourceKey: String = ""
) {
    companion object Companion {
        val Empty = RemoteServerConfig()
    }
}