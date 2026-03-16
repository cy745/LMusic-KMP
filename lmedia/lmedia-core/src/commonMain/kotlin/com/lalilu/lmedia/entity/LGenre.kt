package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "l_genre")
@Serializable
data class LGenre(
    @PrimaryKey
    @ColumnInfo("genre_id")
    var id: String = "",
    var title: String = "",
    var subtitle: String = "",

    @Ignore
    val extra: Map<String, String>? = null
) : LItem,
    Linkable by linkableImpl(),
    Extensible by extensibleImpl(extra) {
    override fun idValue(): String = id
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle
}
