package com.lalilu.llyricview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.llyric.LyricItem
import kotlin.reflect.KClass

interface LyricItemLayout<T : LyricItem> {

    companion object {
        private val map = mutableMapOf<KClass<*>, LyricItemLayout<*>>()

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