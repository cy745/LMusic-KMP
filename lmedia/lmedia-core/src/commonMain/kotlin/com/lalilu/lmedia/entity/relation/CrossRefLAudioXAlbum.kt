package com.lalilu.lmedia.entity.relation

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "cross_ref_audio_x_album",
    primaryKeys = ["album_id", "song_id"]
)
data class CrossRefLAudioXAlbum(
    @ColumnInfo("album_id")
    val albumId: String,
    @ColumnInfo("song_id")
    val songId: String
)
