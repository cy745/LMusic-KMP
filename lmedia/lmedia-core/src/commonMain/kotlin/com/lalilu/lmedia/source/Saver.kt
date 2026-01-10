package com.lalilu.lmedia.source

import kotlin.reflect.KClass

/**
 * 实例接口，用于存储和获取特定键和类型的值
 */
interface Saver {

    /**
     * 初始化默认值
     * @param values 默认值
     */
    fun appendDefaultValues(values: Map<String, Any>) {}

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

    companion object {
        val Empty = object : Saver {}
    }
}