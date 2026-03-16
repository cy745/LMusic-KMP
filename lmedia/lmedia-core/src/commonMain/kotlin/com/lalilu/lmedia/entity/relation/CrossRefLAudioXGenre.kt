package com.lalilu.lmedia.entity.relation

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "cross_ref_audio_x_genre",
    primaryKeys = ["genre_id", "song_id"]
)
data class CrossRefLAudioXGenre(
    @ColumnInfo("genre_id")
    val genreId: String,
    @ColumnInfo("song_id")
    val songId: String
)
