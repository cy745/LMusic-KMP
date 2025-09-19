package com.lalilu.llyric

/**
 * 在歌词项列表中查找指定时间正在播放的歌词索引
 * 使用二分查找算法提高查找效率
 * @param time 当前播放时间（毫秒）
 * @return 正在播放的歌词项索引，如果未找到则返回Int.MAX_VALUE
 */
fun List<LyricItem>.findPlayingIndex(time: Long): Int {
    if (isEmpty()) return Int.MAX_VALUE

    var low = 0
    var high = size - 1
    var result = Int.MAX_VALUE

    while (low <= high) {
        val mid = (low + high) ushr 1
        val midVal = get(mid).time

        when {
            midVal < time -> {
                // 记录最后一个小于目标时间的索引
                result = mid
                low = mid + 1
            }

            midVal > time -> {
                high = mid - 1
            }

            else -> return mid // 找到精确匹配
        }
    }

    // 处理边界情况：
    return when {
        // 所有元素的时间都大于目标时间
        result == Int.MAX_VALUE -> Int.MAX_VALUE

        // 检查找到的索引是否有效（下一个元素时间是否超过当前时间）
        result == lastIndex || get(result + 1).time > time -> result

        // 理论上不会到达这里
        else -> Int.MAX_VALUE
    }
}

/**
 * 在逐字歌词单词列表中查找指定时间正在播放的单词索引
 * 从后往前遍历查找最后一个小于给定时间的单词
 * @param time 当前播放时间（毫秒）
 * @return 正在播放的单词索引，如果未找到则返回Int.MAX_VALUE
 */
fun List<LyricItem.WordsLyric.WordWithTiming>.findPlayingIndexForWords(time: Long): Int {
    for (i in lastIndex downTo 0) {
        if (this[i].startTime < time) return i
    }

    return Int.MAX_VALUE
}

/**
 * 在歌词项列表中查找指定时间正在播放的歌词项
 * 基于 [findPlayingIndex] 方法的结果获取对应的歌词项
 * @param time 当前播放时间（毫秒）
 * @return 正在播放的歌词项，如果未找到则返回null
 */
fun List<LyricItem>.findPlayingItem(time: Long): LyricItem? {
    return this.getOrNull(findPlayingIndex(time))
}

/**
 * 将任意类型的歌词项转换为普通歌词项格式
 * 对于 WordsLyric 类型，会将其内容合并为句子形式，并保留翻译
 * @return 转换后的 NormalLyric 实例，如果无法转换则返回 null
 */
fun LyricItem.toNormal(): LyricItem.NormalLyric? {
    if (this is LyricItem.NormalLyric) return this
    if (this is LyricItem.WordsLyric) apply {
        val translation = translation.firstOrNull { it.content.isNotBlank() }?.content
        val sentence = getSentenceContent()
            .takeIf { it.isNotBlank() }
            ?: return null

        return LyricItem.NormalLyric(
            content = sentence,
            translation = translation,
            time = this.time,
            key = this.key
        )
    }

    return null
}