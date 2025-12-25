package com.lalilu.lmedia.source

import kotlin.reflect.KClass


data class MediaSourceConfig(
    val key: String,
    val name: String = key,
    val description: String = "",
    val initialParams: Map<String, Any> = emptyMap(),
    val paramsDeclarations: List<ParamsDeclaration> = emptyList(),
    val onConfigUpdateCallback: () -> Unit = {}
) {
    private val _params = initialParams.toMutableMap()
    val params: Map<String, Any> get() = _params

    class Builder(
        val key: String = "",
        val name: String = "",
        val description: String = "",
    ) {
        val initialParams = mutableMapOf<String, Any>()
        val paramsDeclarations = mutableListOf<ParamsDeclaration>()
        var onConfigUpdateCallback: (() -> Unit)? = null

        fun declare(declaration: ParamsDeclaration) = apply {
            paramsDeclarations.add(declaration)
        }

        inline fun <reified T : Any> declare(
            key: String,
            name: String = key,
            description: String = "",
            mutable: Boolean = true,
            required: Boolean = false,
            type: KClass<T> = T::class
        ) = ParamsDeclaration(
            key = key,
            name = name,
            description = description,
            mutable = mutable,
            required = required,
            type = type
        ).also { declare(it) }

        inline infix fun <reified T : Any> ParamsDeclaration.provide(value: T) {
            provide<T>(key, value)
        }

        inline fun <reified T : Any> provide(key: String, value: T) = apply {
            val declaration = paramsDeclarations.firstOrNull { it.key == key }
            require(declaration != null) { "[]param with key $key not found" }
            require(declaration.type.isInstance(value)) { "[${this.key}]param with key $key is type ${declaration.type.simpleName}, but got $value" }
            initialParams[key] = value
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
                initialParams = initialParams,
                paramsDeclarations = paramsDeclarations,
                onConfigUpdateCallback = onConfigUpdateCallback!!
            )
        }
    }

    fun update(
        block: (setter: (key: String, value: Any?) -> Unit) -> Unit
    ) {
        block { key, value ->
            val declaration = requireParam(key)
            require(declaration.mutable) { "[${this.key}]param with key $key is not mutable" }
            require(declaration.type.isInstance(value)) {
                "[${this.key}]param with key $key is type ${declaration.type.simpleName}, but got $value"
            }

            if (value == null) {
                _params.remove(key)
            } else {
                _params[key] = value
            }
        }
        onConfigUpdateCallback.invoke()
    }

    inline fun <reified T : Any> require(key: String): T {
        // 校验检查该参数是否已存在声明
        val declaration = requireParam(key)

        // 获取参数值
        val value = params[declaration.key]

        // 校验参数值
        require(value != null) { "[${this.key}]param with key got null ${if (declaration.required) ", for required param" else ""}" }
        require(declaration.type.isInstance(value)) { "[${this.key}]param with key $key is type ${declaration.type.simpleName}, but got $value" }
        require(T::class.isInstance(value)) { "[${this.key}]param with key $key is not of type ${T::class.simpleName}" }

        return value as T
    }

    inline fun <reified T : Any> get(key: String): Result<T> = runCatching { require<T>(key) }

    fun requireParam(key: String): ParamsDeclaration {
        val declaration = paramsDeclarations.firstOrNull { it.key == key }
        require(declaration != null) { "[${this.key}]param with key $key not found" }
        return declaration
    }

    fun getParam(key: String): Result<ParamsDeclaration> = runCatching { requireParam(key) }
}

fun MediaSource.buildConfig(
    key: String,
    name: String = key,
    description: String = "",
    block: MediaSourceConfig.Builder.() -> Unit
): MediaSourceConfig {
    return MediaSourceConfig.Builder(
        key = key,
        name = name,
        description = description
    ).apply(block)
        .callback(::onConfigChange)
        .build()
}