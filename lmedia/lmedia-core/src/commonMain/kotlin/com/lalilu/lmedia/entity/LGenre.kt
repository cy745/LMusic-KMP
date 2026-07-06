package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data class LGenre(
    var id: String = "",
    var title: String = "",
    var subtitle: String = "",
    var extra: Map<String, String>? = null
) : LItem, Linkable, Extensible by extensibleImpl({ extra }) {
    override val refs: MutableMap<KClass<*>, MutableSet<Linkable>> = mutableMapOf()
    override fun idValue(): String = id
    override fun idPrefix(): String = ID_PREFIX
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle

    companion object {
        const val ID_PREFIX = "genre_"
    }
}
