package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable

/**
 * Re-export of [com.lalilu.lmedia.domain.model.Metadata] for JNI compatibility.
 *
 * The native lib-decoder-flac library references
 * "com.lalilu.lmedia.entity.Metadata" directly via JNI calls.
 * Removing this class causes ClassNotFoundException at runtime.
 *
 * TODO: Rebuild native libraries to reference
 *       com.lalilu.lmedia.domain.model.Metadata directly, then delete this file.
 */
@Serializable
data class Metadata(
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val albumArtist: String = "",
    val composer: String = "",
    val lyricist: String = "",
    val comment: String = "",
    val genre: String = "",
    val track: String = "",
    val disc: String = "",
    val date: String = "",
    val duration: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L
) {
    companion object {
        val EMPTY = Metadata()
    }

    fun toMap(): Map<String, String> {
        return mapOf(
            "title" to (title ?: ""),
            "album" to (album ?: ""),
            "artist" to (artist ?: ""),
            "albumArtist" to albumArtist,
            "composer" to composer,
            "lyricist" to lyricist,
            "comment" to comment,
            "genre" to genre,
            "track" to track,
            "disc" to disc,
            "date" to date,
            "duration" to duration.toString(),
            "dateAdded" to dateAdded.toString(),
            "dateModified" to dateModified.toString()
        )
    }
}

/** Convert domain Metadata → entity Metadata (JNI-compatible). */
fun com.lalilu.lmedia.domain.model.Metadata.toEntityMetadata() = Metadata(
    title = this.title, album = this.album, artist = this.artist,
    albumArtist = this.albumArtist, composer = this.composer, lyricist = this.lyricist,
    comment = this.comment, genre = this.genre, track = this.track,
    disc = this.disc, date = this.date, duration = this.duration,
    dateAdded = this.dateAdded, dateModified = this.dateModified
)
