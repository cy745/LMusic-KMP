package com.lalilu.llyricview

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints

/**
 * 歌词组件的上下文环境
 * @param currentTime 获取当前播放时间的函数
 * @param currentIndex 获取当前歌词索引的函数
 * @param isUserScrolling 判断用户是否正在滚动的函数
 * @param screenConstraints 屏幕约束条件
 * @param textMeasurer 文本测量器
 */
@Stable
data class LyricContext(
    val currentTime: () -> Long,
    val currentIndex: () -> Int,
    val isUserScrolling: () -> Boolean,
    val screenConstraints: Constraints,
    val textMeasurer: TextMeasurer,
)