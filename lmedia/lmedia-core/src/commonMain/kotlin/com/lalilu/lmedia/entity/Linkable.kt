package com.lalilu.lmedia.entity

import androidx.room3.Ignore
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
