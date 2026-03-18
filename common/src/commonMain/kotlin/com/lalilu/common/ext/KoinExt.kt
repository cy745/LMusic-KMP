package com.lalilu.common.ext

import dev.whyoleg.sweetspi.Service
import org.koin.core.module.Module
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

typealias KoinModule = Module

@Service
interface KModule {
    fun get(): KoinModule
}

/**
 * Koin 快速获取对象实例
 */
inline fun <reified T> requestFor(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null,
): T? = KoinPlatform.getKoin().getOrNull(T::class, qualifier, parameters)

/**
 * Koin 快速获取对象实例
 */
inline fun <reified T> requestFor(
    vararg key: String
): Set<T> = key.mapNotNull { requestFor<T>(named(it)) }
    .toSet()