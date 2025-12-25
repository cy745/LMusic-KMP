package com.lalilu.lmedia.source

import kotlin.reflect.KClass


data class MediaSourceConfig(
    val key: String,
    val name: String = key,
    val description: String = "",
    val params: Map<String, MediaSourceParam> = emptyMap(),
    val paramsDeclarations: List<ParamsDeclaration> = emptyList(),
    val onConfigUpdateCallback: () -> Unit = {}
) {
    class Builder {
        private var key: String = ""
        private var name: String = ""
        private var description: String = ""
        private val params = mutableMapOf<String, MediaSourceParam>()
        private val paramsDeclarations = mutableListOf<ParamsDeclaration>()
        private var onConfigUpdateCallback: (() -> Unit)? = null

        fun declare(
            key: String,
            name: String = key,
            description: String = "",
            mutable: Boolean = true,
            required: Boolean = false,
            type: KClass<out MediaSourceParam>
        ) = declare(
            ParamsDeclaration(
                key = key,
                name = name,
                description = description,
                mutable = mutable,
                required = required,
                type = type
            )
        )

        fun declare(declaration: ParamsDeclaration) = apply {
            paramsDeclarations.add(declaration)
        }

        fun callback(block: () -> Unit) = apply {
            onConfigUpdateCallback = block
        }

        fun build(): MediaSourceConfig {
            require(key.isNotBlank()) { "MediaSourceConfig key cannot be blank" }
            require(onConfigUpdateCallback != null) { "MediaSourceConfig callback cannot be null" }

            return MediaSourceConfig(
                key = key,
                name = name,
                description = description,
                params = params,
                paramsDeclarations = paramsDeclarations,
                onConfigUpdateCallback = onConfigUpdateCallback!!
            )
        }
    }

    fun update() {
        onConfigUpdateCallback.invoke()
    }
}

fun MediaSource.buildConfig(
    block: MediaSourceConfig.Builder.() -> Unit
): MediaSourceConfig {
    return MediaSourceConfig.Builder()
        .apply(block)
        .callback(::onConfigChange)
        .build()
}