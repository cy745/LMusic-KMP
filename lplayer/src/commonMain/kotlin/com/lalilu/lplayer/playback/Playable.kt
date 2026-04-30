package com.lalilu.lplayer.playback

import com.lalilu.lmedia.entity.Identifiable

sealed interface Playable<T : Identifiable> {
    val key: String

    data class Item<T : Identifiable>(
        val item: T,
        val source: Items<T, *>? = null
    ) : Playable<T> {
        override val key = "${item.idValue()}_${source?.key}"
    }

    data class Items<T : Identifiable, K : Identifiable>(
        val items: List<T>,
        val source: K
    ) : Playable<T> {
        override val key = source.idValue()
    }
}

fun <T : Identifiable> List<Playable<T>>.flatten(): List<Playable.Item<T>> = flatMap { playable ->
    when (playable) {
        is Playable.Item<T> -> listOf(playable)
        is Playable.Items<T, *> -> playable.items.map { item -> Playable.Item(item, playable) }
    }
}