package com.lalilu.lmedia.domain.model

import kotlinx.serialization.Serializable

/** Playback identity. Keep the existing database ID unchanged for legacy callers. */
@Serializable
data class MediaKey(val sourceName: String, val id: String) {
    /** Length-prefixing avoids ambiguity when names or IDs contain separators. */
    val stableKey: String get() = "${sourceName.length}:$sourceName$id"
}

val LAudio.mediaKey: MediaKey get() = MediaKey(mediaSourceName, id)
