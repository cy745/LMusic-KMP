package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable

@Serializable
class LArtist(
    override val id: String,
    override val title: String,
    override val subtitle: String,
    override val extra: Map<String, String> = emptyMap(),
    override val items: List<LAudio> = emptyList()
) : LGroupItem {
}

/**
 * 根据特殊的符号将[LArtist.title]拆分成多个[LArtist]
 */
fun List<LArtist>.separate(): List<LArtist> = flatMap {
    it.title.split('/', ';', '、', ',', '，').map { name ->
        LArtist(
            id = name,
            title = name,
            subtitle = it.subtitle,
            items = it.items
        )
    }
}

/**
 * 根据[LArtist.title]将多个重名的[LArtist]合并
 */
fun List<LArtist>.merge(): List<LArtist> = groupBy { it.title }.map { entry ->
    LArtist(
        id = entry.key,
        title = entry.key,
        subtitle = entry.value.first().subtitle,
        items = entry.value.flatMap { it.items }
    )
}