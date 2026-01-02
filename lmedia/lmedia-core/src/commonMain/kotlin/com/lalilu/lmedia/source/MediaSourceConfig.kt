package com.lalilu.lmedia.source

import com.lalilu.lmedia.InternalLMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

/**
 * 媒体源配置类，用于定义和管理媒体源的配置参数和函数
 *
 * @param key 配置的唯一标识符
 * @param name 配置的显示名称，默认与 key 相同
 * @param description 配置的描述信息
 * @param initialProperties 初始属性值映射
 * @param properties 配置参数声明列表
 * @param functions 可调用函数声明列表
 * @param onConfigUpdateCallback 配置更新时的回调函数
 */
@Suppress("UNCHECKED_CAST")
class MediaSourceConfig(
    val key: String,
    val name: String = key,
    val description: String = "",
    val initialProperties: Map<String, Any> = emptyMap(),
    val properties: List<Declaration.Property<*>> = emptyList(),
    val functions: List<Declaration.Function<*>> = emptyList(),
    val onConfigUpdateCallback: () -> Unit = {}
) : Instance {

    /**
     * 配置值持有者类，用于存储和获取配置值
     */
    inner class ValueHolder(
        val valueMap: Map<String, Any>
    ) {
        /**
         * 获取指定键的配置值，如果值不存在或类型不匹配则抛出异常
         *
         * @param key 配置键
         * @return 配置值
         */
        inline fun <reified T : Any> require(key: String): T {
            val configKey = this@MediaSourceConfig.key

            // 校验检查该参数是否已存在声明
            val declaration = requireProperty<T>(key)

            // 获取参数值
            val value = valueMap[declaration.key]

            // 校验参数值
            require(value != null) { "[$configKey]property with key $key got null ${if (declaration.required) ", for required property" else ""}" }
            require(declaration.type.isInstance(value)) { "[$configKey]property with key $key is type ${declaration.type.simpleName}, but got $value" }
            require(T::class.isInstance(value)) { "[$configKey]property with key $key is not of type ${T::class.simpleName}" }

            return value as T
        }
    }

    /**
     * 获取指定键的属性声明，如果属性不存在则抛出异常
     *
     * @param key 属性键
     * @return 属性声明
     */
    inline fun <reified T : Any> requireProperty(key: String): Declaration.Property<T> {
        val configKey = this@MediaSourceConfig.key
        val declaration = properties.firstOrNull { it.key == key }
        require(declaration != null) { "[$configKey]property with key $key not found" }

        // 验证参数类型
        if (T::class != Any::class) {
            require(declaration.type == T::class) { "[$configKey]property with key $key is type ${declaration.type.simpleName}, but got ${T::class.simpleName}" }
        }

        return declaration as Declaration.Property<T>
    }

    /**
     * 获取指定键的函数声明，如果函数不存在或返回类型不匹配则抛出异常
     *
     * @param key 函数键
     * @return 函数声明
     */
    inline fun <reified T : Any> requireFunction(key: String): Declaration.Function<T> {
        val function = functions.firstOrNull { it.key == key }
        require(function != null) { "[${this.key}]Function with key $key not found" }
        require(function.returnType == T::class) {
            "[${this.key}]function with key $key is type ${function.returnType.simpleName}, but got ${T::class.simpleName}"
        }
        return function as Declaration.Function<T>
    }

    private val _holder = MutableStateFlow(ValueHolder(initialProperties))
    val holder: StateFlow<ValueHolder> get() = _holder


    /**
     * 获取指定键的配置值
     *
     * @param key 配置键
     * @return 包含配置值的结果对象
     */
    inline fun <reified T : Any> get(key: String): Result<T> = holder.value.runCatching { require<T>(key) }

    /**
     * 设置指定键的配置值
     *
     * @param key 配置键
     * @param value 配置值
     */
    inline fun <reified T : Any> set(key: String, value: T?) = update { setter -> setter(key, value) }

    /**
     * 调用指定键的函数
     *
     * @param key 函数键
     * @param args 函数参数
     * @return 包含函数返回值的结果对象
     */
    inline fun <reified T : Any> call(key: String, vararg args: Any?): Result<T> =
        runCatching { requireFunction<T>(key).call(*args) as T }

    /**
     * 更新配置值
     *
     * @param block 更新操作块
     */
    fun update(
        block: (setter: (key: String, value: Any?) -> Unit) -> Unit
    ) {
        val newMap = holder.value.valueMap.toMutableMap()
        block { key, value ->
            val declaration = requireProperty<Any>(key)
            require(declaration.mutable) { "[${this.key}]property with key $key is not mutable" }
            require(declaration.type.isInstance(value)) {
                "[${this.key}]property with key $key is type ${declaration.type.simpleName}, but got $value"
            }

            if (value == null) {
                newMap.remove(key)
            } else {
                newMap[key] = value
            }
        }

        _holder.value = ValueHolder(newMap)
        onConfigUpdateCallback.invoke()
    }

    @InternalLMedia
    override fun getValue(key: String, clazz: KClass<*>): Any? = when (clazz) {
        Int::class -> get<Int>(key)
        Long::class -> get<Long>(key)
        Float::class -> get<Float>(key)
        Double::class -> get<Double>(key)
        Boolean::class -> get<Boolean>(key)
        String::class -> get<String>(key)
        else -> throw IllegalArgumentException("[${this.key}][$key]Unsupported type ${clazz.simpleName}")
    } as Any?

    @InternalLMedia
    override fun setValue(key: String, clazz: KClass<*>, value: Any?) = when (clazz) {
        Int::class -> set<Int>(key, value as Int?)
        Long::class -> set<Long>(key, value as Long?)
        Float::class -> set<Float>(key, value as Float?)
        Double::class -> set<Double>(key, value as Double?)
        Boolean::class -> set<Boolean>(key, value as Boolean?)
        String::class -> set<String>(key, value as String?)
        else -> throw IllegalArgumentException("[${this.key}][$key]Unsupported type ${clazz.simpleName}")
    }
}