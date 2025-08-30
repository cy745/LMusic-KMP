package com.lalilu.lmedia.source.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    @Transient
    val isSuccess: Boolean = status == "ok"

    @Transient
    val isFailed: Boolean = status == "failed"

    @Transient
    val isError: Boolean = error != null

    @Transient
    val errorCode: Int = error?.code ?: 0

    @Transient
    val errorMessage: String = error?.message ?: ""
}