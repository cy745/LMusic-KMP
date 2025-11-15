package com.lalilu.lmedia.entity

interface LGroupItem : LItem {
    val items: List<LAudio>
    val itemsCount: Int
        get() = items.size
}

/**
 * 为实现了LGroupItem接口的对象建立链接关系
 * 
 * 此扩展函数允许任何继承自LGroupItem的类型T调用link()方法，
 * 该方法会遍历当前组内的所有音频项(LAudio)，并为每个音频项建立与当前组的关联
 * 
 * @param T 继承自LGroupItem的具体类型
 */
inline fun <reified T : LGroupItem> T.link() {
    items.forEach { it.link(this) }
}

/**
 * 为LGroupItem对象列表建立链接关系
 * 
 * 此扩展函数作用于LGroupItem类型的列表，会对列表中的每个组项调用其link()方法，
 * 实现批量建立组内音频项与组的关联关系
 * 
 * @param T 继承自LGroupItem的具体类型
 * @return 返回原列表，支持链式调用
 */
inline fun <reified T : LGroupItem> List<T>.link(): List<T> = apply {
    forEach { it.link() }
}