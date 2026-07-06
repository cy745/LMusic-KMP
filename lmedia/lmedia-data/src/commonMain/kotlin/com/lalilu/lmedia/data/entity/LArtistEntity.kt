package com.lalilu.lmedia.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "l_artist")
data class LArtistEntity(
    @PrimaryKey
    @ColumnInfo("artist_id")
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val extra: Map<String, String>? = null,
)
