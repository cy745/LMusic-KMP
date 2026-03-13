package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Entity(tableName = "l_artist")
@Serializable
data class LArtist(
    @PrimaryKey
    @ColumnInfo("artist_id")
    var id: String = "",
    var title: String = "",
    var subtitle: String = "",

    @Ignore
    val extra: Map<String, String> = emptyMap()
) : LItem {
    override fun idValue(): String = id
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle
    override fun extraValue(): Map<String, String> = extra

    @Ignore
    override val refs: MutableMap<KClass<*>, MutableSet<Linkable>> = mutableMapOf()
}

/**
 * 根据特殊的符号将[LArtist.titleValue]拆分成多个[LArtist]
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
 * 根据[LArtist.titleValue]将多个重名的[LArtist]合并
 */
fun List<LArtist>.merge(): List<LArtist> = groupBy { it.title }.map { entry ->
    LArtist(
        id = entry.key,
        title = entry.key,
        subtitle = entry.value.first().subtitle
    ).apply { entry.value.forEach { refs.putAll(it.refs) } }
}