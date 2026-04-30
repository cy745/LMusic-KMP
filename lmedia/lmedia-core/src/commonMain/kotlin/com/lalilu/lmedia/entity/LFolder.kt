package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Entity(tableName = "l_folder", ignoredColumns = ["refs"])
@Serializable
data class LFolder(
    @PrimaryKey
    @ColumnInfo("folder_id")
    var id: String = "",
    var title: String = "",
    var subtitle: String = "",
    var extra: Map<String, String>? = null,
) : LItem, Linkable, Extensible by extensibleImpl({ extra }) {
    override val refs: MutableMap<KClass<*>, MutableSet<Linkable>> = mutableMapOf()
    override fun idValue(): String = "${super.idValue()}$id"
    override fun idPrefix(): String = ID_PREFIX
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle

    companion object {
        const val ID_PREFIX = "folder_"
    }
}
