package com.lalilu.lmedia.source.subsonic

import kotlinx.serialization.Serializable

@Serializable
data class SubsonicError(
    val code: Int = 0,
    val message: String = ""
) {
    override fun toString(): String {
        return "[SubsonicError]: $code - $message"
    }
}