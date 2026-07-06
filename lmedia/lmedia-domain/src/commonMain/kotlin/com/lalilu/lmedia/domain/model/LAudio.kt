package com.lalilu.lmedia.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LAudio(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val mediaSourceName: String = "",
    val metadata: Metadata = Metadata.EMPTY,
    val extra: Map<String, String>? = null,
    override var available: Boolean = true,
) : LItem, Available, TextMatchable,
    Extensible by extensibleImpl({ extra }) {

    // Identifiable
    override fun idValue(): String = id
    override fun idPrefix(): String = ID_PREFIX

    // Describable
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle

    // TextMatchable
    override fun getMatchText(): String = "${title}_${subtitle}"

    companion object {
        const val ID_PREFIX = "audio_"
    }
}
