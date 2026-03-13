package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
class LGenre(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val extra: Map<String, String> = emptyMap()
) : LItem {
    override fun id(): String = id
    override fun title(): String = title
    override fun subtitle(): String = subtitle
    override fun extra(): Map<String, String> = extra
    override val refs: MutableMap<KClass<*>, MutableSet<Linkable>> = mutableMapOf()
}
