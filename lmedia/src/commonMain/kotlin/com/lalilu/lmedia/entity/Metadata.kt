package com.lalilu.lmedia.entity

import kotlinx.serialization.Serializable


@Serializable
data class Metadata(
    val title: String = "",
    val album: String = "",
    val artist: String = "",
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
            "title" to title,
            "album" to album,
            "artist" to artist,
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