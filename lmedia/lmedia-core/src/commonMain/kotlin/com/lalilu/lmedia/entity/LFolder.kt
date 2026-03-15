package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable

@Serializable
class LFolder(
    val id: String,
    val title: String,
    val subtitle: String,
    val extra: Map<String, String>? = null
) : LItem, Linkable by linkableImpl(), Extensible by extensibleImpl(extra) {
    override fun idValue(): String = id
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle
}
