package com.lalilu.lmedia.source

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass


@Serializable
data class MediaSourceConfig(
    val key: String,
    val name: String,
    val description: String = "",
    val params: Map<String, MediaSourceParam>,
    val paramsDeclarations: List<MediaSourceParamsDeclaration>
) {
    class Builder {
        private var key: String = ""
        private var name: String = ""
        private var description: String = ""
        private val params = mutableMapOf<String, MediaSourceParam>()
        private val paramsDeclarations = mutableListOf<MediaSourceParamsDeclaration>()

        fun declareParam(
            key: String,
            type: KClass<out MediaSourceParam>
        ) = MediaSourceParamsDeclaration(
            key = key,
            name = key,
            description = "",
            type = type
        ).also { paramsDeclarations.add(it) }

        fun MediaSourceParamsDeclaration.initParam(value: MediaSourceParam) {
            params[this.key] = value
        }

        fun build(): MediaSourceConfig = MediaSourceConfig(
            key = key,
            name = name,
            description = description,
            params = params,
            paramsDeclarations = paramsDeclarations
        )
    }
}

fun buildConfig(
    block: MediaSourceConfig.Builder.() -> Unit
): MediaSourceConfig {
    return MediaSourceConfig.Builder()
        .apply(block)
        .build()
}