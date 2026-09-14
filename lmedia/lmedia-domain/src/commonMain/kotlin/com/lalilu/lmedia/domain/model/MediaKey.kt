package com.lalilu.lmedia.domain.model

import kotlinx.serialization.Serializable

/** Source-qualified identity; [id] remains the identifier understood by the source. */
@Serializable
data class MediaKey(val sourceName: String, val id: String) {
    /** Length-prefixing avoids ambiguity when names or IDs contain separators. */
    val stableKey: String get() = "${sourceName.length}:$sourceName$id"

    companion object {
        /** Decode only the canonical representation; malformed input must never select a song. */
        fun parse(value: String): MediaKey? {
            val separator = value.indexOf(':')
            if (separator <= 0) return null
            val lengthText = value.substring(0, separator)
            val length = lengthText.toIntOrNull() ?: return null
            if (length < 0 || length.toString() != lengthText) return null
            val payloadStart = separator + 1
            if (length > value.length - payloadStart) return null
            val idStart = payloadStart + length
            return MediaKey(value.substring(payloadStart, idStart), value.substring(idStart))
        }
    }
}

val LAudio.mediaKey: MediaKey get() = MediaKey(mediaSourceName, id)
