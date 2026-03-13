package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
class LFolder(
    val id: String,
    val title: String,
    val subtitle: String,
    val extra: Map<String, String> = emptyMap()
) : LItem {
    override fun idValue(): String = id
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle
    override fun extraValue(): Map<String, String> = extra
    override val refs: MutableMap<KClass<*>, MutableSet<Linkable>> = mutableMapOf()
}
