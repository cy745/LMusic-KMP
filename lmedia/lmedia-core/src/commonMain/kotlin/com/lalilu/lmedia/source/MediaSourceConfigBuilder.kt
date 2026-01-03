package com.lalilu.lmedia.source

import com.lalilu.lmedia.source.Declaration.Parameter
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * 媒体源配置构建器，用于定义媒体源的属性和功能。
 *
 * @param key 配置的唯一标识符
 * @param name 配置的显示名称
 * @param description 配置的描述信息
 */
class MediaSourceConfigBuilder(
    val key: String = "",
    val name: String = "",
    val description: String = "",
) {
    companion object {
        val EMPTY_INSTANCE = object : Instance {}
    }

    val initialProperties = mutableMapOf<String, Any>()
    val properties = mutableListOf<Declaration.Property<*>>()
    val functions = mutableListOf<Declaration.Function<*>>()
    var onConfigUpdateCallback: (() -> Unit)? = null

    /**
     * 声明一个属性或函数到配置中
     */
    fun declare(declaration: Declaration) = apply {
        when (declaration) {
            is Declaration.Property<*> -> properties.add(declaration)
            is Declaration.Function<*> -> functions.add(declaration)
            else -> {}
        }
    }

    /**
     * 定义一个属性
     *
     * @param key 属性的唯一标识符
     * @param name 属性的显示名称
     * @param description 属性的描述信息
     * @param mutable 属性是否可变
     * @param required 属性是否必需
     * @param type 属性的类型，默认为泛型T的类型
     */
    inline fun <reified T : Any> property(
        key: String,
        name: String = key,
        description: String = "",
        priority: Int = 0,
        mutable: Boolean = true,
        required: Boolean = false,
        type: KClass<T> = T::class
    ) = Declaration.Property(
        key = key,
        name = name,
        description = description,
        priority = priority,
        mutable = mutable,
        required = required,
        type = type,
        instance = EMPTY_INSTANCE
    ).also { declare(it) }

    /**
     * 定义一个函数
     *
     * @param key 函数的唯一标识符
     * @param name 函数的显示名称
     * @param description 函数的描述信息
     * @param parameters 函数的参数列表
     * @param returnType 函数的返回类型，默认为泛型T的类型
     */
    inline fun <reified T : Any> function(
        key: String,
        name: String = key,
        description: String = "",
        priority: Int = 0,
        parameters: List<Parameter<*>> = listOf(),
        returnType: KClass<T> = T::class
    ) = Declaration.Function(
        key = key,
        name = name,
        description = description,
        priority = priority,
        parameters = parameters,
        returnType = returnType,
    ).also { declare(it) }

    /**
     * 为属性提供初始值
     *
     * @param value 要设置的初始值
     */
    inline infix fun <reified T : Any> Declaration.Property<T>.provide(value: T) = apply {
        val configKey = this@MediaSourceConfigBuilder.key
        val declaration = properties.firstOrNull { it.key == key }
        require(declaration != null) { "[$configKey]property with key $key not found" }
        require(declaration.type.isInstance(value)) { "[$configKey]property with key $key is type ${declaration.type.simpleName}, but got $value" }
        initialProperties[key] = value
    }

    /**
     * 为函数设置调用回调
     *
     * @param block 函数执行时的回调逻辑
     */
    inline infix fun <reified T : Any> Declaration.Function<T>.onCall(
        crossinline block: (params: Map<String, Any?>) -> T
    ) = apply {
        functions.remove(this)
        return copy(
            callback = { args ->
                val params = this.parameters.mapIndexed { index, parameter ->
                    val value = args.getOrNull(index)
                    parameter.key to when {
                        value == null -> null
                        parameter.type.isInstance(value) -> value
                        else -> parameter.type.cast(value)
                    }
                }.toMap()

                block(params) as? T?
            }
        ).also { declare(it) }
    }

    /**
     * 为函数添加参数
     *
     * @param key 参数的唯一标识符
     * @param name 参数的显示名称
     * @param description 参数的描述信息
     * @param type 参数的类型，默认为泛型K的类型
     */
    inline fun <reified T : Any, reified K : Any> Declaration.Function<T>.parameter(
        key: String,
        name: String = key,
        description: String = "",
        type: KClass<K> = K::class
    ): Declaration.Function<T> {
        functions.remove(this)
        return copy(
            parameters = parameters + Parameter(
                key = key,
                name = name,
                description = description,
                type = type
            )
        ).also { declare(it) }
    }

    /**
     * 设置配置更新时的回调函数
     *
     * @param block 配置更新时执行的回调
     */
    fun callback(block: () -> Unit) = apply {
        onConfigUpdateCallback = block
    }

    /**
     * 构建并返回最终的媒体源配置
     *
     * @return 构建完成的MediaSourceConfig实例
     */
    fun build(): MediaSourceConfig {
        require(key.isNotBlank()) { "MediaSourceConfig key cannot be blank" }
        require(onConfigUpdateCallback != null) { "MediaSourceConfig callback cannot be null" }

        return MediaSourceConfig(
            key = key,
            name = name,
            description = description,
            initialProperties = initialProperties,
            properties = properties,
            functions = functions,
            onConfigUpdateCallback = onConfigUpdateCallback!!
        ).apply {
            // 绑定实例
            properties.forEach { it.instance = this }
        }
    }
}

/**
 * 为MediaSource创建配置
 *
 * @param key 配置的唯一标识符
 * @param name 配置的显示名称
 * @param description 配置的描述信息
 * @param block 配置构建的DSL块
 * @return 构建完成的MediaSourceConfig实例
 */
fun MediaSource.buildConfig(
    key: String,
    name: String = key,
    description: String = "",
    block: MediaSourceConfigBuilder.() -> Unit
): MediaSourceConfig {
    return MediaSourceConfigBuilder(
        key = key,
        name = name,
        description = description
    ).apply(block)
        .callback(::onConfigChange)
        .build()
}