package com.lalilu.lmedia.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LArtist(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val extra: Map<String, String>? = null,
) {
    companion object {
        const val ID_PREFIX = "artist_"
    }
}
