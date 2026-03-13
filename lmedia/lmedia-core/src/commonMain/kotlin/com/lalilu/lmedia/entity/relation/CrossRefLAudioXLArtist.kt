package com.lalilu.lmedia.entity.relation

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "cross_ref_audio_x_artist",
    primaryKeys = ["artist_id", "song_id"]
)
data class CrossRefLAudioXLArtist(
    @ColumnInfo("artist_id")
    val artistId: String,
    @ColumnInfo("song_id")
    val songId: String
)