package com.lalilu.lmedia.entity

/**
 * 定义具有唯一标识符的实体接口。
 * 实现此接口的类必须提供一个返回唯一字符串 ID 的方法。
 */
interface Identifiable {
    /**
     * 获取实体的唯一标识符。
     *
     * @return 代表该实体唯一身份的字符串 ID。
     */
    fun id(): String
}