package com.lalilu.lmedia.entity

/**
 * 定义一个可扩展的接口，允许实现类存储额外的键值对数据。
 */
interface Extensible {

    /**
     * 返回包含额外数据的映射表。
     *
     * @return 一个字符串到字符串的映射，存储额外的元数据或属性。
     */
    fun extraValue(): Map<String, String>
}

/**
 * 创建一个 [Extensible] 的实现实例。
 *
 * @param extra 可选的额外数据映射表。如果为 null，则返回空映射。
 * @return 一个实现了 [Extensible] 接口的对象，其 [Extensible.extraValue] 方法返回提供的映射或空映射。
 */
fun extensibleImpl(extra: () -> Map<String, String>?) = object : Extensible {
    override fun extraValue(): Map<String, String> = extra() ?: emptyMap()
}