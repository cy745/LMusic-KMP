package com.lalilu.preview

import androidx.compose.runtime.Stable


class PreviewScope {
    val dataContext: MutableList<Any> = mutableListOf()

    @Stable
    inline fun <reified T> repeat(count: Int, block: T.() -> Unit) {
        dataContext.filterIsInstance<T>()
            .take(count)
            .forEach { it.block() }
    }

    @Stable
    inline fun <reified T> random(block: T.() -> Unit) {
        dataContext.filterIsInstance<T>()
            .randomOrNull()
            ?.block()
    }
}