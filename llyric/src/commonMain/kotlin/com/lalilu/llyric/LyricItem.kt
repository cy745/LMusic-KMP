package com.lalilu.llyric

/**
 * 歌词项的基类，用于表示各种类型的歌词
 * @param time 歌词的时间戳，默认为0
 * @param key 歌词的唯一标识符，默认为空字符串
 */
sealed class LyricItem(
    open val time: Long = 0,
    open val key: String = "",
) : Comparable<LyricItem> {

    /**
     * 比较两个歌词项的时间戳
     * @param other 要比较的另一个歌词项
     * @return 时间戳的比较结果
     */
    override fun compareTo(other: LyricItem): Int {
        return time.compareTo(other.time)
    }

    /**
     * 普通歌词项，包含内容和可选的翻译
     * @param content 歌词内容
     * @param translation 歌词翻译，可为空
     * @param time 歌词时间戳
     * @param key 歌词唯一标识符
     */
    data class NormalLyric(
        val content: String,
        val translation: String? = null,
        override val time: Long,
        override val key: String
    ) : LyricItem()

    /**
     * 逐字歌词项，包含每个字的精确时间信息
     * @param agent 歌词演唱者
     * @param words 按时间排列的歌词字列表
     * @param translation 歌词翻译列表
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param key 歌词唯一标识符
     */
    data class WordsLyric(
        val agent: String = "",
        val words: List<WordWithTiming>,
        val translation: List<Translation>,
        val startTime: Long,
        val endTime: Long,
        override val key: String,
    ) : LyricItem(time = startTime) {

        /**
         * 获取整句歌词内容
         */
        fun getSentenceContent(): String {
            return words.joinToString(separator = "") { it.content }
        }

        /**
         * 歌词翻译类
         * @param content 翻译内容
         * @param lang 翻译语言
         */
        data class Translation(
            val content: String,
            val lang: String
        )

        /**
         * 带时间信息的歌词字
         * @param content 字的内容
         * @param startTime 开始时间
         * @param endTime 结束时间
         */
        data class WordWithTiming(
            val content: String,
            val startTime: Long,
            val endTime: Long,
        ) : Comparable<WordWithTiming> {
            /**
             * 比较两个带时间信息的歌词字
             * @param other 要比较的另一个歌词字
             * @return 开始时间的比较结果
             */
            override fun compareTo(other: WordWithTiming): Int {
                return startTime.compareTo(other.startTime)
            }
        }
    }

    /**
     * 开始提示项，用于表示歌曲开始前的提示信息
     * @param focusTime 焦点时间
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param key 歌词唯一标识符
     */
    data class StartTips(
        val focusTime: Long,
        val startTime: Long,
        val endTime: Long,
        override val key: String
    ) : LyricItem() {
        override val time: Long = focusTime
    }

    /**
     * 固定提示项，用于表示固定位置的提示信息
     * @param content 提示内容
     * @param time 歌词时间戳
     * @param key 歌词唯一标识符
     */
    data class FixedTips(
        val content: String,
        override val time: Long,
        override val key: String
    ) : LyricItem()
}