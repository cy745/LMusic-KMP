package com.lalilu.common.ext

import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.mp.KoinPlatform

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

context(scope: Scope)
inline fun <reified R : Any> (() -> R).reverseInject(): R = this.invoke()

context(scope: Scope)
inline fun <reified R : Any, reified T1> ((T1) -> R).reverseInject(): R =
    this.invoke(scope.get())

context(scope: Scope)
inline fun <reified R : Any, reified T1, reified T2> ((T1, T2) -> R).reverseInject(): R =
    this.invoke(scope.get(), scope.get())

context(scope: Scope)
inline fun <reified R : Any, reified T1, reified T2, reified T3> ((T1, T2, T3) -> R).reverseInject(): R =
    this.invoke(scope.get(), scope.get(), scope.get())

context(scope: Scope)
inline fun <reified R : Any, reified T1, reified T2, reified T3, reified T4> ((T1, T2, T3, T4) -> R).reverseInject(): R =
    this.invoke(scope.get(), scope.get(), scope.get(), scope.get())

context(scope: Scope)
inline fun <reified R : Any, reified T1, reified T2, reified T3, reified T4, reified T5> ((T1, T2, T3, T4, T5) -> R).reverseInject(): R =
    this.invoke(scope.get(), scope.get(), scope.get(), scope.get(), scope.get())
