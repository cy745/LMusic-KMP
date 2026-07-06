package com.lalilu.lmedia.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LFolder(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val extra: Map<String, String>? = null,
) : LItem,
    Extensible by extensibleImpl({ extra }) {

    override fun idValue(): String = id
    override fun idPrefix(): String = ID_PREFIX
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle

    companion object {
        const val ID_PREFIX = "folder_"
    }
}
