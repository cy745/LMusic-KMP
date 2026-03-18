package com.lalilu.lmedia.entity

/**
 * 定义可被文本匹配的对象接口。
 * 实现此接口的类需要提供用于匹配的文本内容。
 */
interface TextMatchable {
    /**
     * 获取用于文本匹配的字符串。
     *
     * @return 用于匹配的文本内容。
     */
    fun getMatchText(): String
}