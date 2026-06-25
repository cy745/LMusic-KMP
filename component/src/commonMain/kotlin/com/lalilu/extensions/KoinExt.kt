package com.lalilu.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.compose.currentKoinScope
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope


/**
 * 在 Composable 中安全地按需注入 Koin 依赖，注入失败时返回 null 而非抛出异常。
 *
 * 与 [org.koin.compose.inject] 或 [org.koin.compose.koinInject] 的区别：
 * - 不会因依赖未注册而抛异常，适用于可选依赖或懒加载场景
 * - 返回 null 而非 [org.koin.core.error.NoBeanDefFoundException]
 * - 注入结果通过 `remember` 缓存，仅在 qualifier/scope/parameters 变化时重新解析
 *
 * ## 使用场景
 *
 * - 依赖在运行时可能不存在（条件注册、feature flag 控制的模块）
 * - 兼容已有 module 改造，逐步迁移到 Koin 时作为安全的桥接
 * - 不希望 Composable 因注入失败而崩溃，交由调用方处理 null
 *
 * ## 示例
 *
 * ```kotlin
 * // 基本用法
 * val repo = koinInjectOrNull<MyRepository>()
 *
 * // 带 qualifier
 * val service = koinInjectOrNull<MyService>(qualifier = named("remote"))
 *
 * // 带参数
 * val vm = koinInjectOrNull<MyViewModel>(parameters = { parametersOf(id) })
 * ```
 *
 * @param T 要注入的依赖类型（reified，编译期确定）
 * @param qualifier 可选的 Koin Qualifier，用于区分同一接口的多个实现
 * @param scope Koin 作用域，默认使用当前 Composable 的 [currentKoinScope]
 * @param parameters 可选的构造参数，当依赖需要运行时参数时传入
 * @return 注入成功的实例，或 null（依赖未注册时）
 */
@Composable
inline fun <reified T> koinInjectOrNull(
    qualifier: Qualifier? = null,
    scope: Scope = currentKoinScope(),
    noinline parameters: ParametersDefinition? = null,
): T? {
    return remember(qualifier, scope, parameters) {
        if (parameters != null) {
            scope.getOrNull(T::class, qualifier, parameters)
        } else {
            scope.getOrNull(T::class, qualifier)
        }
    }
}
