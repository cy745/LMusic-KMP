@file:Suppress("UNCHECKED_CAST")

package com.lalilu.lmusic.impl

import com.lalilu.common.kv.KVSaver
import com.russhwolf.settings.Settings
import org.koin.core.annotation.Single
import kotlin.reflect.KClass

@Single(createdAtStart = true)
class KvSettingsSaver(
    private val settings: Settings
) : KVSaver {

    override fun <T> readData(key: String, defaultValue: T?, clazz: KClass<*>): T {
        return when {
            defaultValue is Long || clazz == Long::class ->
                settings.getLong(key, (defaultValue as? Long) ?: 0L)

            defaultValue is Int || clazz == Int::class ->
                settings.getInt(key, (defaultValue as? Int) ?: 0)

            defaultValue is Boolean || clazz == Boolean::class ->
                settings.getBoolean(key, (defaultValue as? Boolean) ?: false)

            defaultValue is Float || clazz == Float::class ->
                settings.getFloat(key, (defaultValue as? Float) ?: 0f)

            defaultValue is String || clazz == String::class ->
                settings.getString(key, (defaultValue as? String) ?: "")

            else -> throw UnsupportedOperationException("Not supported type: $clazz, with defaultValue: $defaultValue")
        } as T
    }

    override fun <T> saveData(key: String, value: T?, clazz: KClass<*>) {
        when (value) {
            null -> settings.remove(key)
            is Boolean -> settings.putBoolean(key, value)
            is Int -> settings.putInt(key, value)
            is Long -> settings.putLong(key, value)
            is Float -> settings.putFloat(key, value)
            is String -> settings.putString(key, value)

            else -> throw UnsupportedOperationException("Not supported type: $clazz, with value: $value")
        }
    }
}