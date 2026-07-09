package com.lalilu.lmedia.linkable

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
 */
fun linkableImpl() = object : Linkable {
    override val refs = mutableMapOf<KClass<*>, MutableSet<Linkable>>()
}

/**
 * 将指定的链接项添加到当前对象的引用集合中
 */
inline fun <reified T : Linkable> Linkable.link(item: T) {
    refs.getOrPut(T::class) { mutableSetOf() }.add(item)
}

/**
 * 获取当前对象关联的指定类型的链接项列表
 */
inline fun <reified T : Linkable> Linkable.ref(): List<T> {
    return refs[T::class]?.map { it as T } ?: emptyList()
}

/**
 * 获取当前对象关联的指定类型的链接项数量
 */
inline fun <reified T : Linkable> Linkable.refCount(): Int {
    return refs[T::class]?.count() ?: 0
}

/**
 * 将 [Linkable] 对象列表扁平化，提取并收集所有指定类型的链接项。
 */
inline fun <reified T : Linkable> List<Linkable>.flatten(): List<T> {
    return flatMap {
        if (it is T) return@flatMap listOf(it)
        it.ref<T>()
    }
}

/**
 * 将发射 [Linkable] 对象列表的 Flow 扁平化。
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <reified T : Linkable> Flow<List<Linkable>>.flatten(): Flow<List<T>> {
    return mapLatest { list -> list.flatten<T>() }
}
