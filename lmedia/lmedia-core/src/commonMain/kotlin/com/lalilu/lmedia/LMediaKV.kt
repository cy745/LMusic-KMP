package com.lalilu.lmedia

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.KVSaver
import com.lalilu.lmedia.source.Saver
import org.koin.core.annotation.Single
import kotlin.reflect.KClass

@Single(binds = [Saver::class])
class LMediaKV(saver: KVSaver) : KVContext(_prefix = "lmedia", _saver = saver), Saver {
    /** 数据源成功写入后，是否自动删除数据库中已标记为不可用的歌曲。 */
    val clearUnavailableAfterSync = obtain("clearUnavailableAfterSync", false)

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
