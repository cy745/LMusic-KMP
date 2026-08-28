package com.lalilu.lmedia.data.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "l_audio")
data class LAudioEntity(
    @PrimaryKey
    @ColumnInfo("song_id")
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    @ColumnInfo("media_source_name")
    val mediaSourceName: String = "",
    val extra: Map<String, String>? = null,
    val available: Boolean = true
)
