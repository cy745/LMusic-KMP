package com.lalilu.gradle

import org.gradle.api.Project
import java.util.Properties

private const val DISABLE_IOS_TARGETS = "lalilu.disable.ios.targets"

internal val Project.localProperties: Map<String, Any?>
    get() = rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.let { Properties().apply { load(it.inputStream()) } }
        ?.toMap()
        ?.mapKeys { it.key.toString() }
        ?: emptyMap()

private inline fun <reified T> Project.getFromProperties(key: String, elseValue: () -> T): T {
    return localProperties.safeGetOrNull(key) ?: properties.safeGet(key, elseValue)
}

internal val Project.disableIosTargets: Boolean
    get() = getFromProperties(DISABLE_IOS_TARGETS) { false }

private inline fun <reified T> Map<String, Any?>.safeGet(
    key: String, elseValue: () -> T
): T {
    val value = this[key]
    if (value is T) return value
    return safeGetOrNull(key) ?: elseValue()
}

private inline fun <reified T> Map<String, Any?>.safeGetOrNull(
    key: String
): T? {
    val value = this[key]
    if (value is T) return value
    val strValue = value?.toString() ?: return null

    return when (T::class) {
        String::class -> strValue as T
        Boolean::class -> strValue.toBoolean() as T
        Int::class -> strValue.toInt() as T
        Long::class -> strValue.toLong() as T
        Double::class -> strValue.toDouble() as T
        Float::class -> strValue.toFloat() as T
        Short::class -> strValue.toShort() as T
        Byte::class -> strValue.toByte() as T
        else -> null
    }
}