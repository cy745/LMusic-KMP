package com.lalilu.gradle

import org.gradle.api.Project

private const val DISABLE_IOS_TARGETS = "lalilu.disable.ios.targets"

internal val Project.disableIosTargets: Boolean
    get() = properties.safeGet(DISABLE_IOS_TARGETS) { false }

private inline fun <reified T> Map<String, Any?>.safeGet(
    key: String, elseValue: () -> T
): T {
    val value = this[key]
    if (value is T) return value
    val strValue = value?.toString() ?: return elseValue()

    return when (T::class) {
        String::class -> strValue as T
        Boolean::class -> strValue.toBoolean() as T
        Int::class -> strValue.toInt() as T
        Long::class -> strValue.toLong() as T
        Double::class -> strValue.toDouble() as T
        Float::class -> strValue.toFloat() as T
        Short::class -> strValue.toShort() as T
        Byte::class -> strValue.toByte() as T
        else -> elseValue()
    }
}