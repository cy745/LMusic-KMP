package com.lalilu.lmedia

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver
import com.lalilu.lmedia.source.Saver
import org.koin.core.annotation.Single
import kotlin.reflect.KClass

@Single(binds = [Saver::class])
class LMediaKV(saver: KVSaver) : KVContext(_prefix = "lmedia", _saver = saver), Saver {
    private val cache = mutableMapOf<String, Any?>()
    private val defaultValues = mutableMapOf<String, Any>()

    override fun appendDefaultValues(values: Map<String, Any>) {
        defaultValues.putAll(values)
    }

    override fun getValue(key: String, clazz: KClass<*>): Any? {
        return cache[key]
            ?: _saver?.readData(key, defaultValues[key], clazz)
                ?.also { cache[key] = it }
            ?: defaultValues[key]
    }

    override fun setValue(key: String, clazz: KClass<*>, value: Any?) {
        cache[key] = value
        _saver?.saveData(key, value, clazz)
    }
}