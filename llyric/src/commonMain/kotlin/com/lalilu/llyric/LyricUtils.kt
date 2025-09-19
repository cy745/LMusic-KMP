package com.lalilu.llyric

import com.lalilu.llyric.parser.LrcParser
import com.lalilu.llyric.parser.TtmlParser

/**
 * 歌词解析工具类
 */
object LyricUtils {
    /**
     * 调试模式开关，为true时会在控制台输出解析结果
     */
    var debug = true

    /**
     * 从文本解析双语歌词
     * @param lrcTexts 歌词文本数组，支持多个歌词文本（如原文和译文）
     * @return 解析后的歌词项列表
     */
    fun parseLrc(vararg lrcTexts: String?): List<LyricItem>? {
        val mainLrcText = lrcTexts.getOrNull(0)
            ?: return null

        var result: List<LyricItem>? = null

        // 根据内容判断是否为TTML格式并优先使用TTML解析器
        if (mainLrcText.contains("xmlns:amll") || mainLrcText.contains("xmlns:ttm")) {
            result = TtmlParser.parse(mainLrcText)
        }

        // 如果TTML解析失败或不是TTML格式，则使用LRC解析器
        if (result.isNullOrEmpty()) {
            result = LrcParser.parse(mainLrcText)
        }

        // 为歌词添加开始提示
        result = addStartingTips(result)

        // 调试模式下输出歌词信息到控制台
        if (debug) {
            result.forEach {
                val startTime = when (it) {
                    is LyricItem.NormalLyric -> "[${it.time}]"
                    is LyricItem.WordsLyric -> "[${it.startTime}]"
                    is LyricItem.StartTips -> "[${it.focusTime}] -> [${it.startTime}]"
                    else -> "[${it.time}]"
                }
                val content = when (it) {
                    is LyricItem.StartTips -> " * * * "
                    is LyricItem.NormalLyric -> it.content
                    is LyricItem.WordsLyric -> it.getSentenceContent()
                    is LyricItem.FixedTips -> "[[${it.content}]]"
                }
                val endTime = when (it) {
                    is LyricItem.NormalLyric -> ""
                    is LyricItem.WordsLyric -> "[${it.endTime}]"
                    is LyricItem.StartTips -> "[${it.endTime}]"
                    is LyricItem.FixedTips -> ""
                }
                println("$startTime$content$endTime")
            }
        }

        return result
    }

    /**
     * 为歌词列表添加开始提示项
     * 当两个歌词项之间的时间间隔大于7秒时，会在中间添加一个开始提示
     * @param list 原始歌词项列表
     * @return 添加了开始提示的新歌词项列表
     */
    private fun addStartingTips(list: List<LyricItem>): MutableList<LyricItem> {
        val newList = mutableListOf<LyricItem>()

        for (index in list.indices) {
            val previous = if (index > 0) list[index - 1] else null
            val current = list[index]

            val startTime = previous?.endTime() ?: 0L
            val endTime = current.time

            // 当时间间隔大于7秒时，添加一个开始提示
            if (endTime - startTime >= 7000) {
                newList.add(
                    LyricItem.StartTips(
                        focusTime = startTime,
                        startTime = endTime - 3000,
                        endTime = endTime,
                        key = "pre_${current.key}"
                    )
                )
            }

            newList.add(current)
        }

        return newList
    }

    /**
     * 获取歌词项的结束时间
     * @return 歌词项的结束时间（毫秒）
     */
    private fun LyricItem.endTime(): Long {
        return when (this) {
            is LyricItem.NormalLyric -> time + 7000L
            is LyricItem.StartTips -> endTime
            is LyricItem.WordsLyric -> endTime
            else -> time
        }
    }
}