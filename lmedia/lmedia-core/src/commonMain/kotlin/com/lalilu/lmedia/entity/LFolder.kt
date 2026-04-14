package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "l_folder", ignoredColumns = ["refs"])
@Serializable
data class LFolder(
    @PrimaryKey
    @ColumnInfo("folder_id")
    var id: String = "",
    var title: String = "",
    var subtitle: String = "",
    var extra: Map<String, String>? = null,
) : LItem, Linkable by linkableImpl(), Extensible by extensibleImpl({ extra }) {
    override fun idValue(): String = id
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle
}
