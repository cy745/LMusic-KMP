package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data class LArtist(
    val id: String,
    val title: String,
    val subtitle: String,
    val extra: Map<String, String> = emptyMap()
) : LItem {
    override fun id(): String = id
    override fun title(): String = title
    override fun subtitle(): String = subtitle
    override fun extra(): Map<String, String> = extra
    override val refs: MutableMap<KClass<*>, MutableSet<Linkable>> = mutableMapOf()
}

/**
 * 根据特殊的符号将[LArtist.title]拆分成多个[LArtist]
 */
fun List<LArtist>.separate(): List<LArtist> = flatMap {
    it.title.split('/', ';', '、', ',', '，').map { artist ->
        val name = artist.trim()
        LArtist(
            id = name,
            title = name,
            subtitle = it.subtitle
        ).apply { refs.putAll(it.refs) }
    }
}

/**
 * 根据[LArtist.title]将多个重名的[LArtist]合并
 */
fun List<LArtist>.merge(): List<LArtist> = groupBy { it.title }.map { entry ->
    LArtist(
        id = entry.key,
        title = entry.key,
        subtitle = entry.value.first().subtitle
    ).apply { entry.value.forEach { refs.putAll(it.refs) } }
}