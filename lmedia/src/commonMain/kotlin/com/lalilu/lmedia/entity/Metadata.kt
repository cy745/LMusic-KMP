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
}