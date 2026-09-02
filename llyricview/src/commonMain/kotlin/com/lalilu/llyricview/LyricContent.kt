package com.lalilu.llyricview

import androidx.compose.runtime.Immutable
import com.lalilu.llyric.LyricItem

/**
 * 一首歌曲对应的完整歌词内容。
 *
 * [Loading] 表示尚无可显示的首份歌词；[Ready] 表示一份可以整体切换的歌词文档，
 * 其中空列表明确表示“已加载但没有歌词”。[items] 只读且发布后不再修改。
 */
@Immutable
sealed interface LyricContent {
    val key: String?
    val generation: Long

    data class Loading(
        override val key: String?,
        override val generation: Long = 0L,
    ) : LyricContent

    data class Ready(
        override val key: String?,
        override val generation: Long = 0L,
        val items: List<LyricItem>,
    ) : LyricContent
}
