package com.lalilu.lmedia.entity

import androidx.room3.Ignore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.Transient
import kotlin.reflect.KClass

/**
 * 定义可链接对象的接口。
 *
 * 实现此接口的对象可以维护与其他 [Linkable] 对象的引用关系。
 * [refs] 属性用于存储这些引用，其中键为目标的 Kotlin 类类型，值为目标对象的集合。
 *
 * 注意：该属性在 Room 数据库映射和 Kotlinx 序列化过程中会被忽略。
 */
interface Linkable {
    @get:Ignore
    @Transient
    val refs: MutableMap<KClass<*>, MutableSet<Linkable>>
}

/**
 * 创建一个 [Linkable] 接口的简单实现实例。
 *
 * 该函数返回一个匿名对象，其中 [Linkable.refs] 属性被初始化为空的可变映射。
 * 适用于需要快速构建一个可链接对象但不需要自定义逻辑的场景。
 *
 * @return 一个新的 [Linkable] 实例，其引用集合为空。
 */
fun linkableImpl() = object : Linkable {
    override val refs = mutableMapOf<KClass<*>, MutableSet<Linkable>>()
}

/**
 * 将指定的链接项添加到当前对象的引用集合中
 *
 * @param item 要链接的项，必须是 Linkable 的实现
 */
inline fun <reified T : Linkable> Linkable.link(item: T) {
    refs.getOrPut(T::class) { mutableSetOf() }.add(item)
}

/**
 * 获取当前对象关联的指定类型的链接项列表
 *
 * @param T 要获取的链接项类型，必须实现自 Linkable
 * @return 包含所有关联的指定类型链接项的列表，如果没有则返回空列表
 */
inline fun <reified T : Linkable> Linkable.ref(): List<T> {
    return refs[T::class]?.map { it as T } ?: emptyList()
}

/**
 * 获取当前对象关联的指定类型的链接项数量
 *
 * @param T 要统计的链接项类型，必须实现自 Linkable
 * @return 关联的指定类型链接项的数量，如果没有则返回 0
 */
inline fun <reified T : Linkable> Linkable.refCount(): Int {
    return refs[T::class]?.count() ?: 0
}

/**
 * 将 [Linkable] 对象列表扁平化，提取并收集所有指定类型的链接项。
 *
 * 该扩展函数遍历列表中的每个 [Linkable] 对象，调用其 [ref] 方法获取指定类型 [T] 的关联项，
 * 并将结果合并为一个单一的列表返回。
 *
 * @param T 要提取的链接项类型，必须实现自 [Linkable]
 * @return 包含所有嵌套的指定类型链接项的扁平化列表
 */
inline fun <reified T : Linkable> List<Linkable>.flatten(): List<T> {
    return flatMap {
        if (it is T) return@flatMap listOf(it)
        it.ref<T>()
    }
}

/**
 * 将发射 [Linkable] 对象列表的 Flow 扁平化，提取并收集所有指定类型的链接项。
 *
 * 该扩展函数对 Flow 发射的每个列表应用 [flatten] 操作，将其中嵌套的指定类型 [T] 的链接项
 * 提取并合并为新的列表进行发射。使用 [mapLatest] 确保只处理最新的列表数据。
 *
 * @param T 要提取的链接项类型，必须实现自 [Linkable]
 * @return 一个发射扁平化后的指定类型链接项列表的 Flow
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <reified T : Linkable> Flow<List<Linkable>>.flatten(): Flow<List<T>> {
    return mapLatest { list -> list.flatten<T>() }
}
