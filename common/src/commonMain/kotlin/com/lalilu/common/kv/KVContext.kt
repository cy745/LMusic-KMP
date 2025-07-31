package com.lalilu.common.kv

import com.lalilu.common.kv.impl.KVItemImpl
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
abstract class KVContext(
    val _prefix: String = "",
    val _saver: KVSaver? = kvSaver
) {
    inline fun <reified T : Any> obtain(
        key: String,
        defaultValue: T? = null,
        prefix: String = _prefix,
        typeParameters: List<KClass<*>> = emptyList(),
        saver: KVSaver? = _saver
    ): KVItem<T> = obtainStatic(key, defaultValue, prefix, typeParameters, saver)

    inline fun <reified T> obtainList(
        key: String,
        defaultValue: List<T> = emptyList<T>(),
        prefix: String = _prefix,
        saver: KVSaver? = _saver
    ): KVItem<List<T>> = obtain<List<T>>(
        key = key,
        defaultValue = defaultValue,
        prefix = prefix,
        typeParameters = listOf(T::class),
        saver = saver
    )

    inline fun <reified T> obtainSet(
        key: String,
        defaultValue: Set<T> = emptySet<T>(),
        prefix: String = _prefix,
        saver: KVSaver? = _saver
    ): KVItem<Set<T>> = obtain<Set<T>>(
        key = key,
        defaultValue = defaultValue,
        prefix = prefix,
        typeParameters = listOf(T::class),
        saver = saver
    )

    inline fun <reified T, reified K> obtainMap(
        key: String,
        defaultValue: Map<T, K> = emptyMap<T, K>(),
        prefix: String = _prefix,
        saver: KVSaver? = _saver
    ): KVItem<Map<T, K>> = obtain<Map<T, K>>(
        key = key,
        defaultValue = defaultValue,
        prefix = prefix,
        typeParameters = listOf(T::class, K::class),
        saver = saver
    )

    companion object {
        val kvMap = LinkedHashMap<String, KVItem<*>>()
        var kvSaver: KVSaver? = null
            private set

        /**
         * 注册KV保存器
         */
        fun registerSaver(kvSaver: KVSaver) {
            this.kvSaver = kvSaver
        }

        /**
         * 注册KV转换器
         */
        fun registerConverter(converter: KVConverter) {
            KVConverter.converters += converter
        }

        /**
         * 静态创建或获取已存在的KVItem，新创建时将自动注册
         *
         * @param key 唯一键
         * @param defaultValue 默认值
         * @param prefix 区分前缀
         * @param typeParameters 类型参数列表(当需要构建泛型相关类时需传入类型参数列表)
         * @param saver 实际的数据读写对象
         *
         * @return KVItem<T> 实例
         */
        inline fun <reified T : Any> obtainStatic(
            key: String,
            defaultValue: T? = null,
            prefix: String = "",
            typeParameters: List<KClass<*>> = emptyList(),
            saver: KVSaver? = kvSaver
        ): KVItem<T> {
            val actualKey = if (prefix.isNotBlank()) "${prefix}_$key" else key
            return kvMap.getOrPut(actualKey) {
                KVItemImpl(
                    key = actualKey,
                    clazz = T::class,
                    defaultValue = defaultValue,
                    converter = KVConverter.findConverter<T>(defaultValue, typeParameters),
                    saver = saver
                )
            } as KVItem<T>
        }
    }
}

