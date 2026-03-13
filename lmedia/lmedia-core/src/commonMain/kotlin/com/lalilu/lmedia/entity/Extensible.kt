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
    fun extra(): Map<String, String>
}