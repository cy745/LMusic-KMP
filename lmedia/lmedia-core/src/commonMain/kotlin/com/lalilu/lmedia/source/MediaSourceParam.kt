package com.lalilu.lmedia.source

import kotlinx.serialization.Serializable

@Serializable
sealed interface MediaSourceParam {
    data class StringV(val value: String) : MediaSourceParam
    data class IntV(val value: Int) : MediaSourceParam
    data class LongV(val value: Long) : MediaSourceParam
    data class BooleanV(val value: Boolean) : MediaSourceParam
    data class FloatV(val value: Float) : MediaSourceParam
}
