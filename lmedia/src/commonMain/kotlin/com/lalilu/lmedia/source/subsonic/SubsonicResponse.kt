package com.lalilu.lmedia.source.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
open class SubsonicResponse(
    val status: String = "",
    val version: String = "",
    val type: String = "",
    @SerialName("serverVersion")
    val serverVersion: String = "",
    @SerialName("openSubsonic")
    val openSubsonic: Boolean = true,
    val error: SubsonicError? = null
) {
    val isSuccess: Boolean = status == "ok"
    val isFailed: Boolean = status == "failed"
    val isError: Boolean = error != null
    val errorCode: Int = error?.code ?: 0
    val errorMessage: String = error?.message ?: ""
}