package com.lalilu.lmedia.source

import kotlin.reflect.KClass

/**
 * 内存存储器实现，将数据保存在内存映射表中
 * 这是一个单例对象，用于临时存储键值对数据
 */
object MemorySaver : Saver {
    private val map = mutableMapOf<String, Any?>()
    private val defaultValues = mutableMapOf<String, Any>()

    override fun appendDefaultValues(values: Map<String, Any>) {
        defaultValues.putAll(values)
    }

    /**
     * 从内存映射表中获取指定键对应的值
     * @param key 键名
     * @param clazz 值的类型（在此实现中未使用，因为内存存储不需要类型转换）
     * @return 对应的值，如果不存在则返回 null
     */
    override fun getValue(key: String, clazz: KClass<*>): Any? {
        return map[key] ?: defaultValues[key]
    }

    /**
     * 向内存映射表中设置指定键和值
     * @param key 键名
     * @param clazz 值的类型（在此实现中未使用）
     * @param value 要设置的值
     */
    override fun setValue(key: String, clazz: KClass<*>, value: Any?) = run { map[key] = value }
}