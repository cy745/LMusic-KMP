package com.lalilu.llyricview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.impl.LyricFixedTipsContent
import com.lalilu.llyricview.impl.LyricNormalContent
import com.lalilu.llyricview.impl.LyricStartTipsContent
import com.lalilu.llyricview.impl.LyricWordsContent
import kotlin.reflect.KClass

interface LyricItemLayout<T : LyricItem> {

    companion object {
        private val map = mutableMapOf<KClass<*>, LyricItemLayout<*>>(
            LyricItem.NormalLyric::class to LyricNormalContent,
            LyricItem.StartTips::class to LyricStartTipsContent,
            LyricItem.WordsLyric::class to LyricWordsContent,
            LyricItem.FixedTips::class to LyricFixedTipsContent
        )

        @Suppress("UNCHECKED_CAST")
        fun <T : LyricItem> get(item: T): LyricItemLayout<T>? {
            return map[item::class] as? LyricItemLayout<T>
        }

        fun set(item: KClass<*>, layout: LyricItemLayout<*>) {
            map[item] = layout
        }
    }

    @Composable
    fun content(
        index: Int,
        item: T,
        modifier: Modifier = Modifier,
        settings: LyricSettings,
        context: LyricContext,
        onClick: (() -> Unit)?,
        onLongClick: (() -> Unit)?,
    )
}