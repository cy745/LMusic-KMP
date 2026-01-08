package com.lalilu.lmedia.source

import kotlin.reflect.KClass


/**
 * 实例接口，用于存储和获取特定键和类型的值
 */
interface Instance {
    /**
     * 获取指定键和类型的值
     * @param key 键名
     * @param clazz 值的类型
     * @return 对应的值，如果不存在则返回 null
     */
    fun getValue(key: String, clazz: KClass<*>): Any? = Unit

    /**
     * 设置指定键和类型的值
     * @param key 键名
     * @param clazz 值的类型
     * @param value 要设置的值
     */
    fun setValue(key: String, clazz: KClass<*>, value: Any?) = Unit
}

@Suppress("UNCHECKED_CAST")
/**
 * 声明基类，定义了所有声明的公共属性
 * @param key 声明的唯一标识符
 * @param name 声明的名称
 * @param description 声明的描述
 * @param priority 优先级，默认为0
 */
sealed class Declaration(
    open val key: String,        // 声明的唯一标识符
    open val name: String,       // 声明的名称
    open val description: String, // 声明的描述
    open val priority: Int = 0,  // 优先级
) {
    /**
     * 属性声明，用于定义可读写的属性
     * @param T 属性的类型
     * @param key 声明的唯一标识符
     * @param name 声明的名称
     * @param description 声明的描述
     * @param priority 优先级
     * @param instance 存储属性值的实例
     * @param mutable 是否可变，默认为true
     * @param required 是否必需，默认为false
     * @param type 属性类型
     */
    data class Property<T : Any>(
        override val key: String,
        override val name: String,
        override val description: String,
        override val priority: Int = 0,
        var instance: Instance,    // 存储属性值的实例
        val mutable: Boolean = true,   // 是否可变
        val required: Boolean = false, // 是否必需
        val type: KClass<T>,           // 属性类型
    ) : Declaration(
        key = key,
        name = name,
        description = description,
        priority = priority,
    ) {
        /**
         * 设置属性值
         * @param value 要设置的值
         */
        fun set(value: T?) = instance.setValue(key, type, value)

        /**
         * 获取属性值
         * @return 属性的当前值，如果不存在则返回 null
         */
        fun get(): T? = instance.getValue(key, type) as? T?
    }

    /**
     * 参数声明，用于函数参数定义
     * @param T 参数的类型
     * @param key 声明的唯一标识符
     * @param name 声明的名称
     * @param description 声明的描述
     * @param priority 优先级
     * @param type 参数类型
     */
    data class Parameter<T : Any>(
        override val key: String,
        override val name: String,
        override val description: String,
        override val priority: Int = 0,
        val type: KClass<T>,      // 参数类型
    ) : Declaration(
        key = key,
        name = name,
        description = description,
        priority = priority,
    )

    /**
     * 函数声明，用于定义可调用的函数
     * @param T 函数返回值的类型
     * @param key 声明的唯一标识符
     * @param name 声明的名称
     * @param description 声明的描述
     * @param priority 优先级
     * @param parameters 函数参数列表
     * @param returnType 返回值类型
     * @param isAvailable 是否可用的检测回调
     * @param callback 函数执行回调
     */
    data class Function<T : Any>(
        override val key: String,
        override val name: String,
        override val description: String,
        override val priority: Int = 0,
        val parameters: List<Parameter<*>>,                 // 函数参数列表
        val returnType: KClass<T>,                          // 返回值类型
        val isAvailable: () -> Boolean = { true },          // 是否可用的检测回调
        val callback: ((args: List<Any?>) -> T?)? = null,   // 函数执行回调
    ) : Declaration(
        key = key,
        name = name,
        description = description,
        priority = priority,
    ) {
        /**
         * 调用函数，传入参数并返回结果
         * @param args 函数参数
         * @return 函数执行结果，如果回调为null则返回null
         */
        fun call(vararg args: Any?): T? = callback?.invoke(args.toList())
    }
}
