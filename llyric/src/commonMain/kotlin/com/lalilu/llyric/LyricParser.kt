package com.lalilu.llyric

/**
 * 歌词解析器接口，定义了解析歌词字符串的方法
 */
interface LyricParser {
    /**
     * 解析歌词字符串为歌词项列表
     * @param lyric 歌词字符串内容
     * @return 解析后的歌词项列表
     */
    fun parse(lyric: String): List<LyricItem>
}