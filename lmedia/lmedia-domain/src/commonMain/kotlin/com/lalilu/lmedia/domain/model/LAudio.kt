package com.lalilu.lmedia.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LAudio(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val mediaSourceName: String = "",
    val extra: Map<String, String>? = null,
    val available: Boolean = true,
) {
    /** A single reversible identity for queue entries and platform playback requests. */
    val playbackId: String get() = mediaKey.stableKey

    companion object {
        const val ID_PREFIX = "audio_"
    }
}
