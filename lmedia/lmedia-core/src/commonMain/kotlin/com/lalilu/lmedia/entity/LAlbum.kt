package com.lalilu.lmedia.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.lalilu.lmedia.sortable.Sortable
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Entity(tableName = "l_album", ignoredColumns = ["refs"])
@Serializable
data class LAlbum(
    @PrimaryKey
    @ColumnInfo("album_id")
    var id: String = "",
    var title: String = "",
    var subtitle: String = "",
    var extra: Map<String, String>? = null,
) : LItem, Sortable, TextMatchable, Linkable,
    Extensible by extensibleImpl({ extra }) {
    override val refs: MutableMap<KClass<*>, MutableSet<Linkable>> = mutableMapOf()
    override fun idValue(): String = id
    override fun idPrefix(): String = ID_PREFIX
    override fun titleValue(): String = title
    override fun subtitleValue(): String = subtitle

    override fun getMatchText(): String = "$title $subtitle"

    @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")
    override fun <T : Any> getValueBy(key: String): T? {
        return when (key) {
            Sortable.COMPARE_KEY_ID -> id
            Sortable.COMPARE_KEY_TITLE -> title
            Sortable.COMPARE_KEY_SUB_TITLE -> subtitle
            Sortable.COMPARE_KEY_ITEMS_COUNT -> ref<LAudio>().size
            else -> super.getValueBy(key)
        } as? T?
    }

    companion object {
        const val ID_PREFIX = "album_"
    }
}