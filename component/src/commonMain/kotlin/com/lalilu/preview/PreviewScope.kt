package com.lalilu.preview

import androidx.compose.runtime.Stable


class PreviewScope {
    val dataContext: MutableList<Any> = mutableListOf<Any>()
        .apply { addAll(SongsPreviewData) }

    fun enableNetworkImage() = CoilImageHandler
        .enableNetworkImage()

    @Stable
    inline fun <reified T> repeat(
        count: Int,
        key: String? = null,
        shuffle: Boolean = false,
        block: T.() -> Unit
    ) {
        dataContext.filterIsInstance<T>()
            .run {
                if (T::class.isInstance(PreviewPresets.EMPTY) && key != null) {
                    filterIsInstance<PreviewPresets>()
                        .filter { it.key == key }
                } else {
                    this
                }
            }
            .let { if (shuffle) it.shuffled() else it }
            .take(count)
            .forEach { (it as? T)?.block() }
    }

    @Stable
    inline fun <reified T> random(
        key: String? = null,
        block: T.() -> Unit
    ) {
        dataContext.filterIsInstance<T>()
            .run {
                if (T::class.isInstance(PreviewPresets.EMPTY) && key != null) {
                    filterIsInstance<PreviewPresets>()
                        .filter { it.key == key }
                } else {
                    this
                }
            }
            .randomOrNull()
            ?.let { (it as? T)?.block() }
    }
}