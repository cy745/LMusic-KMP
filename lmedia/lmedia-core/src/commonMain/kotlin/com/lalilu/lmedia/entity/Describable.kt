package com.lalilu.lmedia.entity

/**
 * 定义可描述对象的接口。
 * 实现此接口的类应提供标题和副标题信息。
 */
interface Describable {
    /**
     * 获取对象的标题。
     * @return 标题字符串。
     */
    fun title(): String

    /**
     * 获取对象的副标题。
     * @return 副标题字符串。
     */
    fun subtitle(): String
}