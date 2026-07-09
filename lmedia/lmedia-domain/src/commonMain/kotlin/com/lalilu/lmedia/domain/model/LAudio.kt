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
    val available: Boolean = true,
) {
    companion object {
        const val ID_PREFIX = "audio_"
    }
}
